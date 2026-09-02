WITH permission_catalog(code, module, description) AS (
    VALUES
        ('SALE_READ', 'SALES', 'View sales'),
        ('SALE_CREATE', 'SALES', 'Create sales'),
        ('SALE_UPDATE', 'SALES', 'Update sales'),
        ('SALE_DELETE', 'SALES', 'Delete sales'),
        ('SALE_CANCEL', 'SALES', 'Cancel sales'),
        ('CLIENT_READ', 'CLIENTS', 'View clients'),
        ('CLIENT_CREATE', 'CLIENTS', 'Create clients'),
        ('CLIENT_UPDATE', 'CLIENTS', 'Update clients'),
        ('CLIENT_DELETE', 'CLIENTS', 'Delete clients'),
        ('PRODUCT_READ', 'PRODUCTS', 'View products'),
        ('PRODUCT_CREATE', 'PRODUCTS', 'Create products'),
        ('PRODUCT_UPDATE', 'PRODUCTS', 'Update products'),
        ('PRODUCT_DELETE', 'PRODUCTS', 'Delete products'),
        ('PRICE_READ', 'PRICES', 'View product prices'),
        ('PRICE_CREATE', 'PRICES', 'Create product prices'),
        ('PRICE_UPDATE', 'PRICES', 'Update product prices'),
        ('PRICE_DELETE', 'PRICES', 'Delete product prices'),
        ('USER_READ', 'USERS', 'View users'),
        ('USER_CREATE', 'USERS', 'Create users'),
        ('USER_UPDATE', 'USERS', 'Update users'),
        ('USER_DELETE', 'USERS', 'Delete users'),
        ('USER_ASSIGN_ROLE', 'USERS', 'Assign roles to users'),
        ('USER_ASSIGN_PERMISSION', 'USERS', 'Assign personal permission overrides to users'),
        ('ROLE_READ', 'ROLES', 'View roles'),
        ('ROLE_CREATE', 'ROLES', 'Create roles'),
        ('ROLE_UPDATE', 'ROLES', 'Update roles'),
        ('ROLE_DELETE', 'ROLES', 'Delete roles'),
        ('ROLE_ASSIGN_PERMISSION', 'ROLES', 'Assign permissions to roles'),
        ('PERMISSION_READ', 'PERMISSIONS', 'View permissions'),
        ('PERMISSION_CREATE', 'PERMISSIONS', 'Create permissions'),
        ('PERMISSION_UPDATE', 'PERMISSIONS', 'Update permissions'),
        ('PERMISSION_DELETE', 'PERMISSIONS', 'Delete permissions'),
        ('ROUTE_READ', 'ROUTES', 'View route sales'),
        ('ROUTE_CREATE', 'ROUTES', 'Create route sales'),
        ('ROUTE_UPDATE', 'ROUTES', 'Update route sales'),
        ('ROUTE_DELETE', 'ROUTES', 'Delete route sales'),
        ('ROUTE_CANCEL', 'ROUTES', 'Cancel route sales'),
        ('DRIVER_READ', 'DRIVERS', 'View drivers'),
        ('DRIVER_CREATE', 'DRIVERS', 'Create drivers'),
        ('DRIVER_UPDATE', 'DRIVERS', 'Update drivers'),
        ('DRIVER_DELETE', 'DRIVERS', 'Delete drivers')
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

CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    effect VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    created_by UUID,
    CONSTRAINT fk_user_permission_overrides_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permission_overrides_permission
        FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_permission_overrides_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_user_permission_overrides_user_permission
        UNIQUE (user_id, permission_id),
    CONSTRAINT ck_user_permission_overrides_effect
        CHECK (effect IN ('ALLOW', 'DENY'))
);

CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_user_id
    ON user_permission_overrides(user_id);

CREATE INDEX IF NOT EXISTS idx_user_permission_overrides_permission_id
    ON user_permission_overrides(permission_id);

INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE UPPER(role.name) = 'ADMIN'
ON CONFLICT DO NOTHING;
