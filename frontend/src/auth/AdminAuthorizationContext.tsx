import type { ReactElement, ReactNode } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  issueAdminAuthorization,
  registerAdminAuthorizationPrompt,
  type AdminAuthorizationTarget,
} from "../api/admin-authorizations";
import AdminAuthorizationModal from "../components/AdminAuthorizationModal";
import { ApiRequestError } from "../api/errors";

interface PendingAuthorization {
  target: AdminAuthorizationTarget;
  resolve: (token: string | null) => void;
}

export function AdminAuthorizationProvider({ children }: { children: ReactNode }): ReactElement {
  const [pending, setPending] = useState<PendingAuthorization | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pendingRef = useRef<PendingAuthorization | null>(null);
  const busyRef = useRef(false);

  const prompt = useCallback((target: AdminAuthorizationTarget): Promise<string | null> => {
    return new Promise((resolve) => {
      const request = { target, resolve };
      pendingRef.current = request;
      setError("");
      setPending(request);
    });
  }, []);

  useEffect(() => {
    registerAdminAuthorizationPrompt(prompt);
    return () => {
      registerAdminAuthorizationPrompt(null);
      pendingRef.current?.resolve(null);
      pendingRef.current = null;
    };
  }, [prompt]);

  const close = useCallback((token: string | null): void => {
    const current = pendingRef.current;
    pendingRef.current = null;
    setPending(null);
    busyRef.current = false;
    setBusy(false);
    setError("");
    current?.resolve(token);
  }, []);

  const authorize = useCallback(async (username: string, password: string): Promise<void> => {
    const current = pendingRef.current;
    if (!current || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      const token = await issueAdminAuthorization(current.target, { username, password });
      close(token);
    } catch (caught) {
      setError(
        caught instanceof ApiRequestError
          ? caught.message
          : "No fue posible validar las credenciales del administrador.",
      );
      busyRef.current = false;
      setBusy(false);
    }
  }, [close]);

  return (
    <>
      {children}
      {pending ? (
        <AdminAuthorizationModal
          busy={busy}
          error={error}
          onAuthorize={authorize}
          onCancel={() => close(null)}
        />
      ) : null}
    </>
  );
}
