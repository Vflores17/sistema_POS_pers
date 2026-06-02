import type { ReactElement } from "react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import styles from "./Dashboard.module.css";

export default function Dashboard(): ReactElement {
  const navigate = useNavigate();

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent): void {
      if (e.altKey && e.key.toLowerCase() === "v") {
        e.preventDefault();
        navigate("/sales");
      }
      if (e.altKey && e.key.toLowerCase() === "r") {
        e.preventDefault();
        navigate("/route-sales");
      }
      if (e.altKey && e.key.toLowerCase() === "p") {
        e.preventDefault();
        navigate("/products");
      }
      if (e.altKey && e.key.toLowerCase() === "c") {
        e.preventDefault();
        navigate("/clients");
      }
      if (e.altKey && e.key.toLowerCase() === "u") {
        e.preventDefault();
        navigate("/users");
      }
      if (e.altKey && e.key.toLowerCase() === "s") {
        e.preventDefault();
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        window.location.href = "/login";
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [navigate]);

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
          <span><u>V</u>entas</span>
        </a>
        <a href="/route-sales" className={styles.menuItem}>
          <span className={styles.menuIcon}>🚚</span>
          <span><u>R</u>utas</span>
        </a>
        <a href="/products" className={styles.menuItem}>
          <span className={styles.menuIcon}>📦</span>
          <span><u>P</u>roductos</span>
        </a>
        <a href="/clients" className={styles.menuItem}>
          <span className={styles.menuIcon}>👥</span>
          <span><u>C</u>lientes</span>
        </a>
        <a href="/users" className={styles.menuItem}>
          <span className={styles.menuIcon}>👤</span>
          <span><u>U</u>suarios</span>
        </a>
        <a href="#"
          className={styles.menuItem}
          onClick={() => {
            localStorage.removeItem("token");
            localStorage.removeItem("refreshToken");
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