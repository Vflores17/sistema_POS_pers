import { useNavigate } from "react-router-dom";
import styles from "./NotFound.module.css";

export default function NotFound() {
  const navigate = useNavigate();

  return (
    <main className={styles.page}>
      <section className={styles.content} aria-labelledby="not-found-title">
        <div className={styles.hero} aria-hidden="true">
          <span className={styles.code}>404</span>

          <div className={styles.vineLeft}>
            <span className={`${styles.vineLeaf} ${styles.vineLeafOne}`} />
            <span className={`${styles.vineLeaf} ${styles.vineLeafTwo}`} />
            <span className={`${styles.vineLeaf} ${styles.vineLeafThree}`} />
          </div>

          <div className={styles.vineRight}>
            <span className={`${styles.vineLeaf} ${styles.vineLeafFour}`} />
            <span className={`${styles.vineLeaf} ${styles.vineLeafFive}`} />
          </div>

          <div className={styles.wiltedPlant}>
            <span className={styles.wiltedStem} />
            <span className={`${styles.wiltedLeaf} ${styles.wiltedLeafLeft}`} />
            <span className={`${styles.wiltedLeaf} ${styles.wiltedLeafRight}`} />
            <span className={styles.potRim} />
            <span className={styles.pot} />
            <span className={styles.potShadow} />
          </div>
        </div>

        <div className={styles.message}>
          <p className={styles.kicker}>Página no encontrada</p>
          <h1 id="not-found-title" className={styles.title}>
            Esta ruta no floreció
          </h1>
          <p className={styles.description}>
            La página que buscas no existe o fue movida.
          </p>
          <button
            className={styles.button}
            type="button"
            onClick={() => navigate("/dashboard")}
          >
            Volver al Dashboard
          </button>
        </div>
      </section>
    </main>
  );
}
