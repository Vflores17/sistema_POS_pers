import type { ReactElement } from "react";
import styles from "./Dashboard.module.css";

export default function Dashboard(): ReactElement {
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
        <a href="/sales" className={styles.menuItem}>
          <span className={styles.menuIcon}>🧾</span>
          Ventas
        </a>
        <a href="/route-sales" className={styles.menuItem}>
          <span className={styles.menuIcon}>🚚</span>
          Rutas
        </a>
        <a href="/products" className={styles.menuItem}>
          <span className={styles.menuIcon}>📦</span>
          Productos
        </a>
        <a href="/clients" className={styles.menuItem}>
          <span className={styles.menuIcon}>👥</span>
          Clientes
        </a>
        <a href="/users" className={styles.menuItem}>
          <span className={styles.menuIcon}>👤</span>
          Usuarios
        </a>
        <a href="#"
          className={styles.menuItem}
          onClick={() => {
            localStorage.removeItem("token");
            window.location.href = "/login";
          }}
        >
          <span className={styles.menuIcon}>🚪</span>
          Salir
        </a>
      </nav>
    </section >
  );
}
