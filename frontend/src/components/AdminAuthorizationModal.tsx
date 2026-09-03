import type { FormEvent, ReactElement } from "react";
import { useEffect, useRef, useState } from "react";
import styles from "./AdminAuthorizationModal.module.css";

interface AdminAuthorizationModalProps {
  busy: boolean;
  error: string;
  onAuthorize: (username: string, password: string) => Promise<void>;
  onCancel: () => void;
}

export default function AdminAuthorizationModal({
  busy,
  error,
  onAuthorize,
  onCancel,
}: AdminAuthorizationModalProps): ReactElement {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const usernameRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    usernameRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape" && !busy) {
        event.preventDefault();
        setPassword("");
        onCancel();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, onCancel]);

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (busy || !username.trim() || !password) return;
    await onAuthorize(username.trim(), password);
  }

  return (
    <div className={styles.backdrop} role="presentation">
      <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="admin-auth-title">
        <h2 id="admin-auth-title">Autorización requerida</h2>
        <p>No tienes permiso para realizar esta acción.</p>
        <p>Se requiere autorización de un administrador.</p>
        <form onSubmit={(event) => void submit(event)}>
          <label htmlFor="admin-username">Usuario administrador</label>
          <input
            ref={usernameRef}
            id="admin-username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={busy}
            required
          />
          <label htmlFor="admin-password">Contraseña</label>
          <input
            id="admin-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={busy}
            required
          />
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <div className={styles.actions}>
            <button type="button" onClick={onCancel} disabled={busy}>Cancelar</button>
            <button className={styles.authorize} type="submit" disabled={busy || !username.trim() || !password}>
              {busy ? "Validando..." : "Autorizar"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
