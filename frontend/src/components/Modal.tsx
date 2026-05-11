import type { ReactElement } from "react";
import { useEffect } from "react";
import styles from "./Modal.module.css";

interface ModalProps {
    type?: "success" | "error" | "confirm" | "warning";
    title: string;
    message: string;
    onConfirm?: () => void;
    onCancel?: () => void;
    confirmLabel?: string;
    cancelLabel?: string;
    danger?: boolean;
}

export default function Modal({
    type = "success",
    title,
    message,
    onConfirm,
    onCancel,
    confirmLabel = "Aceptar",
    cancelLabel = "Cancelar",
    danger = false, // 👈
}: ModalProps): ReactElement {

    useEffect(() => {
        function handleKeyDown(e: KeyboardEvent): void {
            if (e.key === "Enter" && onConfirm) {
                e.preventDefault();
                onConfirm();
            }
            if (e.key === "Escape" && onCancel) {
                e.preventDefault();
                onCancel();
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [onConfirm, onCancel]);

    const icon = {
        success: "✅",
        error: "❌",
        confirm: "⚠️",
        warning: "⚠️",
    }[type];

    return (
        <div className={styles.backdrop}>
            <div className={`${styles.modal} ${styles[type]}`}>
                <div className={styles.icon}>{icon}</div>
                <h3 className={styles.title}>{title}</h3>
                <p className={styles.message} style={{ whiteSpace: "pre-line" }}>{message}</p>                <div className={styles.actions}>
                    {onCancel && (
                        <button className={styles.cancelButton} type="button" onClick={onCancel}>
                            {cancelLabel} <kbd>Esc</kbd>
                        </button>
                    )}
                    {onConfirm && (
                        <button
                            className={danger ? styles.dangerButton : styles.confirmButton}
                            type="button"
                            onClick={onConfirm}
                        >
                            {confirmLabel} <kbd>Enter</kbd>
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}