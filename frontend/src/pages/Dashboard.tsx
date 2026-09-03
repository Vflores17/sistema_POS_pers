import type { ReactElement } from "react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./Dashboard.module.css";
import { usePermissions } from "../auth/PermissionContext";
import SkeletonBlock from "../components/SkeletonBlock";

type ModuleIcon = "sales" | "routes" | "products" | "clients" | "users";

interface ModuleLinkProps {
  href: string;
  icon: ModuleIcon;
  shortcut: string;
  title: string;
  description: string;
}

function LineIcon({ name }: { name: ModuleIcon | "logout" | "leaf" }): ReactElement {
  const paths = {
    sales: <><path d="M7 3h10l2 4v14H5V7l2-4Z"/><path d="M8 11h8M8 15h5M9 3v4h6V3"/></>,
    routes: <><path d="M3 6h11v11H3zM14 10h4l3 4v3h-7z"/><path d="M7 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/></>,
    products: <><path d="m4 7 8-4 8 4-8 4-8-4Z"/><path d="M4 7v10l8 4 8-4V7M12 11v10"/></>,
    clients: <><path d="M16 20v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M19 8v6M22 11h-6"/></>,
    users: <><circle cx="9" cy="8" r="4"/><path d="M3 21v-2a6 6 0 0 1 12 0v2M18 8h3M19.5 6.5v3"/></>,
    logout: <><path d="M10 17l5-5-5-5M15 12H3"/><path d="M14 3h6v18h-6"/></>,
    leaf: <><path d="M5 21c0-8 5-14 14-17 0 9-4 15-12 16"/><path d="M6 19c3-5 6-8 11-11"/></>,
  };

  return <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">{paths[name]}</svg>;
}

function ModuleLink({ href, icon, shortcut, title, description }: ModuleLinkProps): ReactElement {
  return (
    <a href={href} className={styles.menuItem}>
      <span className={styles.menuIcon}><LineIcon name={icon} /></span>
      <span className={styles.cardCopy}>
        <strong><u>{shortcut}</u>{title.slice(1)}</strong>
        <small>{description}</small>
      </span>
      <span className={styles.cardArrow} aria-hidden="true">→</span>
    </a>
  );
}

export default function Dashboard(): ReactElement {
  const navigate = useNavigate();
  const { hasPermission, clearSession, loading, currentUser } = usePermissions();

  function logout(): void {
    localStorage.removeItem("token");
    localStorage.removeItem("refreshToken");
    clearSession();
    window.location.href = "/login";
  }

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent): void {
      if (e.altKey && e.key.toLowerCase() === "v") {
        e.preventDefault();
        if (hasPermission("SALE_READ")) navigate("/sales");
      }
      if (e.altKey && e.key.toLowerCase() === "r") {
        e.preventDefault();
        if (hasPermission("ROUTE_READ")) navigate("/route-sales");
      }
      if (e.altKey && e.key.toLowerCase() === "p") {
        e.preventDefault();
        if (hasPermission("PRODUCT_READ")) navigate("/products");
      }
      if (e.altKey && e.key.toLowerCase() === "c") {
        e.preventDefault();
        if (hasPermission("CLIENT_READ")) navigate("/clients");
      }
      if (e.altKey && e.key.toLowerCase() === "u") {
        e.preventDefault();
        if (hasPermission("USER_READ")) navigate("/users");
      }
      if (e.altKey && e.key.toLowerCase() === "s") {
        e.preventDefault();
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        clearSession();
        window.location.href = "/login";
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [navigate, hasPermission, clearSession]);

  if (loading) {
    return (
      <section className={styles.page} aria-label="Cargando dashboard" aria-busy="true">
        <div className={styles.dashboardShell}>
          <header className={styles.header}>
            <div><SkeletonBlock className={styles.skeletonDate} /><SkeletonBlock className={styles.skeletonTitle} /></div>
            <SkeletonBlock className={styles.skeletonSession} />
          </header>
          <div className={styles.menuGrid}>
            {Array.from({ length: 5 }, (_, index) => (
              <div className={styles.skeletonMenuItem} key={index}>
                <SkeletonBlock className={styles.skeletonIcon} />
                <div className={styles.skeletonCopy}><SkeletonBlock className={styles.skeletonLabel} /><SkeletonBlock className={styles.skeletonDescription} /></div>
              </div>
            ))}
          </div>
        </div>
      </section>
    );
  }

  const date = new Date().toLocaleDateString("es-CR", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  });

  return (
    <section className={styles.page}>
      <div className={styles.botanicalWatermark} aria-hidden="true">
        <svg className={styles.watermarkRight} viewBox="0 0 460 360" fill="none">
          <path d="M447 347C355 319 310 260 278 196C246 132 202 84 126 42" />
          <path d="M326 280C350 242 382 219 421 205M281 204C323 183 350 147 364 105M230 132C249 91 279 61 317 42M189 85C167 53 139 31 103 19" />
          <path d="M397 214C413 188 433 178 453 179C449 201 432 216 397 214ZM348 137C363 108 386 94 411 94C406 121 384 139 348 137ZM281 66C297 37 318 24 343 25C338 51 318 68 281 66ZM153 62C128 62 110 48 103 25C128 23 146 36 153 62ZM261 180C232 180 212 164 206 140C232 138 253 151 261 180ZM334 262C306 259 288 242 285 217C311 218 330 234 334 262Z" />
        </svg>
        <svg className={styles.watermarkLeft} viewBox="0 0 210 190" fill="none">
          <path d="M8 183C49 156 72 119 88 76C99 47 119 26 151 9" />
          <path d="M68 123C46 114 29 96 21 75M88 77C112 75 132 64 147 44" />
          <path d="M48 112C28 109 16 96 13 78C32 78 46 89 48 112ZM118 65C126 44 141 34 160 34C156 53 143 65 118 65Z" />
        </svg>
      </div>
      <div className={styles.dashboardShell}>
        <header className={styles.header}>
          <div className={styles.brandBlock}>
            <span className={styles.brandIcon}><LineIcon name="leaf" /></span>
            <div>
              <p className={styles.date}>{date}</p>
              <h1 className={styles.title}>Vivero Hermanos Flores</h1>
              <p className={styles.intro}>Selecciona un módulo para comenzar.</p>
            </div>
          </div>
          <div className={styles.sessionBlock}>
            <div className={styles.userMeta}>
              <span>Sesión activa</span>
              <strong>{currentUser?.fullName || currentUser?.username}</strong>
            </div>
            <button className={styles.logoutButton} type="button" onClick={logout} title="Cerrar sesión (Alt+S)">
              <LineIcon name="logout" />
              <span>Salir</span>
            </button>
          </div>
        </header>

        <div className={styles.sectionHeading}>
          <h2>Módulos</h2>
          <span>Accesos disponibles para tu perfil</span>
        </div>

        <nav className={styles.menuGrid} aria-label="Módulos del sistema">
          {hasPermission("SALE_READ") && <ModuleLink href="/sales" icon="sales" shortcut="V" title="Ventas" description="Facturación e historial" />}
          {hasPermission("ROUTE_READ") && <ModuleLink href="/route-sales" icon="routes" shortcut="R" title="Rutas" description="Pedidos y entregas" />}
          {hasPermission("PRODUCT_READ") && <ModuleLink href="/products" icon="products" shortcut="P" title="Productos" description="Catálogo e inventario" />}
          {hasPermission("CLIENT_READ") && <ModuleLink href="/clients" icon="clients" shortcut="C" title="Clientes" description="Directorio comercial" />}
          {hasPermission("USER_READ") && <ModuleLink href="/users" icon="users" shortcut="U" title="Usuarios" description="Accesos y permisos" />}
        </nav>
      </div>
    </section>
  );
}
