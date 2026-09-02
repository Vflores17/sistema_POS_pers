import type { ReactElement } from "react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./Dashboard.module.css";
import { usePermissions } from "../auth/PermissionContext";
import SkeletonBlock from "../components/SkeletonBlock";

export default function Dashboard(): ReactElement {
  const navigate = useNavigate();
  const { hasPermission, clearSession, loading } = usePermissions();

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
        <header className={styles.header}>
          <SkeletonBlock className={styles.skeletonDate} />
          <SkeletonBlock className={styles.skeletonTitle} />
        </header>
        <div className={styles.menuGrid}>
          {Array.from({ length: 6 }, (_, index) => (
            <div className={styles.skeletonMenuItem} key={index}>
              <SkeletonBlock className={styles.skeletonIcon} />
              <SkeletonBlock className={styles.skeletonLabel} />
            </div>
          ))}
        </div>
      </section>
    );
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <p className={styles.date}>
          {new Date().toLocaleDateString("es-CR", {
            weekday: "long",
            year: "numeric",
            month: "long",
            day: "numeric",
          })}
        </p>
        <h2 className={styles.title}>🌿 Vivero Hermanos Flores</h2>
      </header>

      <nav className={styles.menuGrid}>
        {hasPermission("SALE_READ") && <a href="/sales" className={styles.menuItem}>
          <span className={styles.menuIcon}>🧾</span>
          <span><u>V</u>entas</span>
        </a>}
        {hasPermission("ROUTE_READ") && <a href="/route-sales" className={styles.menuItem}>
          <span className={styles.menuIcon}>🚚</span>
          <span><u>R</u>utas</span>
        </a>}
        {hasPermission("PRODUCT_READ") && <a href="/products" className={styles.menuItem}>
          <span className={styles.menuIcon}>📦</span>
          <span><u>P</u>roductos</span>
        </a>}
        {hasPermission("CLIENT_READ") && <a href="/clients" className={styles.menuItem}>
          <span className={styles.menuIcon}>👥</span>
          <span><u>C</u>lientes</span>
        </a>}
        {hasPermission("USER_READ") && <a href="/users" className={styles.menuItem}>
          <span className={styles.menuIcon}>👤</span>
          <span><u>U</u>suarios</span>
        </a>}
        <a href="#"
          className={styles.menuItem}
          onClick={() => {
            localStorage.removeItem("token");
            localStorage.removeItem("refreshToken");
            clearSession();
            window.location.href = "/login";
          }}
        >
          <span className={styles.menuIcon}>🚪</span>
          <span><u>S</u>alir</span>
        </a>
      </nav>
    </section>
  );
}
