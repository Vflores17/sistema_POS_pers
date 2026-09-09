-- BUILD 11.1 (AUDIT 11, F1) — Username case-insensitive uniqueness.
--
-- MANUAL SCRIPT: NOT part of the automatic Flyway flow (db/manual, applied by
-- an operator on the production Postgres instance, like the other V2026 manual
-- scripts). NOT applied on dev/test (H2 + ddl-auto).
--
-- Analysis of references to users(id) performed during the audit:
--   * sales.user_id            -> plain column, NO FK  -> never blocks deletes
--   * route_sales.user_id      -> plain column, NO FK  -> never blocks deletes
--   * refresh_tokens.user_id   -> FK (no action)       -> delete/revoke rows of the
--                                                          removed users first
--   * user_roles               -> join table FK        -> delete rows of the removed
--                                                          users first
--   * user_permission_overrides.user_id   -> CASCADE   -> safe
--   * user_permission_overrides.created_by -> SET NULL -> safe
--   * admin_authorizations.requester_user_id /
--     approver_user_id         -> ON DELETE RESTRICT   -> BLOCKS deletion; requires
--                                                          a manual decision
--
-- FAIL-CLOSED POLICY:
--   * This script NEVER deletes or merges rows automatically.
--   * If no case-insensitive duplicate exists, it only installs the unique index
--     ux_users_username_lower.
--   * If duplicates exist, it raises an exception listing the groups and requires
--     a human operator to decide the surviving account and perform the merge, then
--     re-run the script. The application code remains safe with historical
--     duplicates (deterministic resolution in UserRepository.findAllForAuthentication).

DO $$
DECLARE
    dup RECORD;
    dup_count INTEGER := 0;
BEGIN
    FOR dup IN
        SELECT lower(username) AS normalized_name, count(*) AS amount
        FROM users
        GROUP BY lower(username)
        HAVING count(*) > 1
        ORDER BY lower(username)
    LOOP
        RAISE NOTICE 'Duplicate username (case-insensitive): "%" (% rows) — manual merge required',
            dup.normalized_name, dup.amount;
        dup_count := dup_count + 1;
    END LOOP;

    IF dup_count > 0 THEN
        RAISE EXCEPTION
            'Username dedupe blocked: found % case-insensitive duplicate group(s). '
            'Manual intervention required before creating the unique index: decide the '
            'surviving account for each group, repoint admin_authorizations '
            '(requester_user_id/approver_user_id) to the survivors, delete refresh_tokens '
            'and user_roles rows of the removed users, then re-run this script.',
            dup_count
            USING ERRCODE = 'P0001';
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_lower
    ON users (lower(username));