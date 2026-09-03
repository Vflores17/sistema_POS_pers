import type { ReactElement } from "react";
import { useEffect, useRef } from "react";
import styles from "./GlobalErrorModal.module.css";

interface GlobalErrorModalProps {
  message: string;
  onClose: () => void;
}

export default function GlobalErrorModal({ message, onClose }: GlobalErrorModalProps): ReactElement {
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    buttonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape" || event.key === "Enter") {
        event.preventDefault();
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  return (
    <div className={styles.backdrop} role="presentation">
      <section
        className={styles.modal}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="global-error-title"
        aria-describedby="global-error-message"
      >
        <span className={styles.marker} aria-hidden="true">!</span>
        <h2 id="global-error-title">No se pudo completar la acción</h2>
        <p id="global-error-message">{message}</p>
        <button ref={buttonRef} type="button" onClick={onClose}>Aceptar</button>
      </section>
    </div>
  );
}
