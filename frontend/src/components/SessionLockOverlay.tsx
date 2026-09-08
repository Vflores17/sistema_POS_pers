import type { FormEvent, ReactElement } from "react";
import { useEffect, useRef, useState } from "react";
import styles from "./SessionLockOverlay.module.css";

interface SessionLockOverlayProps {
  busy: boolean;
  error: string;
  onUnlock: (username: string, password: string) => Promise<void>;
  onLogout: () => void;
}

export default function SessionLockOverlay({
  busy,
  error,
  onUnlock,
  onLogout,
}: SessionLockOverlayProps): ReactElement {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const usernameRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    usernameRef.current?.focus();
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (busy || !username.trim() || !password) return;
    await onUnlock(username.trim(), password);
  }

  return (
    <div className={styles.backdrop} role="presentation">
      <section className={styles.modal} role="dialog" aria-modal="true" aria-labelledby="session-lock-title">
        <h2 id="session-lock-title">Sesión bloqueada</h2>
        <p>La aplicación se bloqueó por inactividad.</p>
        <p>Ingresa tus credenciales para continuar con tu sesión abierta.</p>
        <form onSubmit={(event) => void submit(event)}>
          <label htmlFor="session-lock-username">Usuario</label>
          <input
            ref={usernameRef}
            id="session-lock-username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            disabled={busy}
            required
          />
          <label htmlFor="session-lock-password">Contraseña</label>
          <input
            id="session-lock-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            disabled={busy}
            required
          />
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          <div className={styles.actions}>
            <button type="button" onClick={onLogout} disabled={busy}>Cerrar sesión</button>
            <button className={styles.unlock} type="submit" disabled={busy || !username.trim() || !password}>
              {busy ? "Desbloqueando..." : "Desbloquear"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}