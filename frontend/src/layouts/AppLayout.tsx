import type { ReactElement } from "react";
import { NavLink, Outlet } from "react-router-dom";
import styles from "./AppLayout.module.css";
import { usePermissions } from "../auth/PermissionContext";

export default function AppLayout(): ReactElement {
  const { hasPermission } = usePermissions();
  return (
    <div className={styles.container}>
      <aside className={styles.sidebar}>
        <h1 className={styles.title}>POS Vivero</h1>
        <nav className={styles.nav}>
          {hasPermission("USER_READ") && <NavLink
            to="/dashboard"
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            Dashboard
          </NavLink>}
          {hasPermission("CLIENT_READ") && <NavLink
            to="/users"
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            Usuarios
          </NavLink>}
          {hasPermission("PRODUCT_READ") && <NavLink
            to="/clients"
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            Clientes
          </NavLink>}
          {hasPermission("SALE_READ") && <NavLink
            to="/products"
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            Productos
          </NavLink>}
          <NavLink
            to="/sales"
            className={({ isActive }) =>
              isActive ? `${styles.link} ${styles.active}` : styles.link
            }
          >
            Ventas
          </NavLink>
        </nav>
      </aside>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}
