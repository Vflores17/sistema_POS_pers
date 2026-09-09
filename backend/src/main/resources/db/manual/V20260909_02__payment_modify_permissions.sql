WITH permission_catalog(code, module, description) AS (
    VALUES
        ('SALE_PAYMENT_MODIFY', 'SALES', 'Replace payments of an existing sale invoice'),
        ('ROUTE_PAYMENT_MODIFY', 'ROUTES', 'Replace payments of an existing route sale invoice')
)
INSERT INTO permissions (id, code, module, description, created_at, updated_at)
SELECT (
           substr(md5('permission:' || code), 1, 8) || '-' ||
           substr(md5('permission:' || code), 9, 4) || '-' ||
           substr(md5('permission:' || code), 13, 4) || '-' ||
           substr(md5('permission:' || code), 17, 4) || '-' ||
           substr(md5('permission:' || code), 21, 12)
       )::uuid,
       code,
       module,
       description,
       NOW(),
       NOW()
FROM permission_catalog
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE UPPER(role.name) = 'ADMIN'
  AND permission.code IN ('SALE_PAYMENT_MODIFY', 'ROUTE_PAYMENT_MODIFY')
ON CONFLICT DO NOTHING;