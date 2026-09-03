import type { ReactElement } from "react";
import SkeletonBlock from "./SkeletonBlock";
import styles from "./ModuleLoadingSkeleton.module.css";

interface ModuleLoadingSkeletonProps {
  columns: number;
  formFields?: number;
  rows?: number;
  variant?: "management" | "history";
}

export default function ModuleLoadingSkeleton({
  columns,
  formFields = 0,
  rows = 7,
  variant = "management",
}: ModuleLoadingSkeletonProps): ReactElement {
  return (
    <div className={styles.shell} aria-label="Cargando datos" aria-busy="true">
      <section className={styles.container}>
        <header className={styles.header}>
          <SkeletonBlock className={styles.title} />
          <SkeletonBlock className={styles.headerAction} />
        </header>
        {variant === "history" ? (
          <div className={styles.filters}>
            {Array.from({ length: 5 }, (_, index) => (
              <div className={styles.filterField} key={index}>
                <SkeletonBlock className={styles.label} />
                <SkeletonBlock className={styles.control} />
              </div>
            ))}
            <SkeletonBlock className={styles.action} />
            <SkeletonBlock className={styles.action} />
          </div>
        ) : formFields > 0 ? (
          <div className={styles.formCard}>
            {Array.from({ length: formFields }, (_, index) => (
              <div className={styles.formField} key={index}>
                <SkeletonBlock className={styles.label} />
                <SkeletonBlock className={styles.control} />
              </div>
            ))}
            <SkeletonBlock className={styles.formButton} />
          </div>
        ) : null}
        <div className={styles.tableCard}>
          {variant === "management" ? <SkeletonBlock className={styles.search} /> : null}
          <div className={styles.table} style={{ minWidth: `${columns * 118}px` }}>
            <div className={styles.tableHeader} style={{ gridTemplateColumns: `repeat(${columns}, minmax(90px, 1fr))` }}>
              {Array.from({ length: columns }, (_, index) => <SkeletonBlock key={index} />)}
            </div>
            {Array.from({ length: rows }, (_, row) => (
              <div className={styles.tableRow} style={{ gridTemplateColumns: `repeat(${columns}, minmax(90px, 1fr))` }} key={row}>
                {Array.from({ length: columns }, (_, column) => (
                  <SkeletonBlock key={column} style={{ width: column === 0 ? "72%" : "58%" }} />
                ))}
              </div>
            ))}
          </div>
        </div>
        <footer className={styles.footer}>
          <div>{Array.from({ length: variant === "history" ? 6 : 2 }, (_, index) => <SkeletonBlock key={index} />)}</div>
          <div>{Array.from({ length: variant === "history" ? 3 : 1 }, (_, index) => <SkeletonBlock key={index} />)}</div>
        </footer>
      </section>
    </div>
  );
}
