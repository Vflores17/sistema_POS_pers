import type { ReactElement, ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { login } from "../api/auth";
import { performLogout } from "../api/http";
import { ApiRequestError } from "../api/errors";
import SessionLockOverlay from "../components/SessionLockOverlay";
import {
  LAST_ACTIVITY_KEY,
  broadcastLockEvent,
  readSharedLastActivity,
  subscribeToLockEvents,
  writeSharedLastActivity,
} from "../api/lockSync";
import { usePermissions } from "./PermissionContext";

const IDLE_TIMEOUT_MS = 30 * 60 * 1000;
const ACTIVITY_THROTTLE_MS = 5_000;
const TOKEN_KEY = "token";

interface SessionLockContextValue {
  blocked: boolean;
}

const SessionLockContext = createContext<SessionLockContextValue | null>(null);

export function SessionLockProvider({ children }: { children: ReactNode }): ReactElement {
  const { clearSession, reloadPermissions } = usePermissions();
  const [blocked, setBlocked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const blockedRef = useRef(false);
  const busyRef = useRef(false);
  const lastActivityRef = useRef(0);
  const timerRef = useRef<number | null>(null);
  const evaluateRef = useRef<() => void>(() => undefined);

  const blockLocally = useCallback((at: number, propagate: boolean): void => {
    if (blockedRef.current) return;
    lastActivityRef.current = Math.max(lastActivityRef.current, at);
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = null;
    blockedRef.current = true;
    setBlocked(true);
    if (propagate) broadcastLockEvent({ type: "lock:blocked", at });
  }, []);

  const schedule = useCallback((): void => {
    if (timerRef.current !== null) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null;
      evaluateRef.current();
    }, IDLE_TIMEOUT_MS);
  }, []);

  const exitBlocked = useCallback((at: number, propagate: boolean): void => {
    lastActivityRef.current = Math.max(lastActivityRef.current, at);
    writeSharedLastActivity(lastActivityRef.current);
    blockedRef.current = false;
    setBlocked(false);
    if (propagate) broadcastLockEvent({ type: "lock:unlocked", at: lastActivityRef.current });
    schedule();
  }, [schedule]);

  const evaluate = useCallback((): void => {
    if (blockedRef.current) return;
    if (!localStorage.getItem(TOKEN_KEY)) return;
    const effective = Math.max(lastActivityRef.current, readSharedLastActivity());
    if (Date.now() - effective >= IDLE_TIMEOUT_MS) {
      blockLocally(effective, true);
    } else {
      schedule();
    }
  }, [blockLocally, schedule]);

  useEffect(() => {
    evaluateRef.current = evaluate;
  });

  const unlock = useCallback(async (username: string, password: string): Promise<void> => {
    if (!blockedRef.current || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      await login(username, password);
      await reloadPermissions();
      exitBlocked(Date.now(), true);
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "No fue posible desbloquear la sesión. Verifica tus credenciales.",
      );
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, [reloadPermissions, exitBlocked]);

  const handleLockedLogout = useCallback((): void => {
    if (!blockedRef.current || busyRef.current) return;
    void performLogout().then(() => {
      clearSession();
      window.location.href = "/login";
    });
  }, [clearSession]);

  useEffect(() => {
    const handleActivity = (): void => {
      if (blockedRef.current) return;
      if (!localStorage.getItem(TOKEN_KEY)) return;
      const now = Date.now();
      if (now - lastActivityRef.current < ACTIVITY_THROTTLE_MS) return;
      lastActivityRef.current = now;
      writeSharedLastActivity(now);
      schedule();
    };
    const onStorage = (event: StorageEvent): void => {
      if (event.key === LAST_ACTIVITY_KEY && event.newValue !== null) {
        const at = Number(event.newValue);
        if (Number.isFinite(at) && at > lastActivityRef.current) {
          lastActivityRef.current = at;
          if (!blockedRef.current) schedule();
        }
        return;
      }
      if (event.key === TOKEN_KEY && event.newValue === null) {
        if (blockedRef.current) {
          blockedRef.current = false;
          setBlocked(false);
        }
        if (timerRef.current !== null) window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
    window.addEventListener("storage", onStorage);
    window.addEventListener("pointerdown", handleActivity, { passive: true });
    window.addEventListener("keydown", handleActivity, { passive: true });
    window.addEventListener("mousemove", handleActivity, { passive: true });
    window.addEventListener("touchstart", handleActivity, { passive: true });
    window.addEventListener("wheel", handleActivity, { passive: true });
    window.addEventListener("scroll", handleActivity, { passive: true });
    return () => {
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("pointerdown", handleActivity);
      window.removeEventListener("keydown", handleActivity);
      window.removeEventListener("mousemove", handleActivity);
      window.removeEventListener("touchstart", handleActivity);
      window.removeEventListener("wheel", handleActivity);
      window.removeEventListener("scroll", handleActivity);
    };
  }, [schedule]);

  useEffect(() => {
    return subscribeToLockEvents((event) => {
      if (event.type === "lock:unlocked") {
        exitBlocked(event.at, false);
        return;
      }
      blockLocally(event.at, false);
    });
  }, [exitBlocked, blockLocally]);

  useEffect(() => {
    lastActivityRef.current = Date.now();
    writeSharedLastActivity(lastActivityRef.current);
    if (localStorage.getItem(TOKEN_KEY)) schedule();
    return () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current);
      timerRef.current = null;
    };
  }, [schedule]);

  const value = useMemo<SessionLockContextValue>(() => ({ blocked }), [blocked]);

  return (
    <SessionLockContext.Provider value={value}>
      {children}
      {blocked ? (
        <SessionLockOverlay busy={busy} error={error} onUnlock={unlock} onLogout={handleLockedLogout} />
      ) : null}
    </SessionLockContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSessionLock(): SessionLockContextValue {
  const context = useContext(SessionLockContext);
  if (!context) throw new Error("useSessionLock must be used inside SessionLockProvider");
  return context;
}