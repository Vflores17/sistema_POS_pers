import type { ReactElement, ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { getCurrentUser, type CurrentUser } from "../api/users";

interface PermissionContextValue {
  currentUser: CurrentUser | null;
  loading: boolean;
  hasPermission: (code: string) => boolean;
  hasAllPermissions: (...codes: string[]) => boolean;
  reloadPermissions: () => Promise<void>;
  clearSession: () => void;
}

const PermissionContext = createContext<PermissionContextValue | null>(null);

export function PermissionProvider({ children }: { children: ReactNode }): ReactElement {
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(Boolean(localStorage.getItem("token")));

  const reloadPermissions = useCallback(async (): Promise<void> => {
    if (!localStorage.getItem("token")) {
      setCurrentUser(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      setCurrentUser(await getCurrentUser());
    } catch {
      setCurrentUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timeout = window.setTimeout(() => { void reloadPermissions(); }, 0);
    return () => window.clearTimeout(timeout);
  }, [reloadPermissions]);
  useEffect(() => {
    const refreshAfterForbidden = (): void => { void reloadPermissions(); };
    window.addEventListener("permissions-forbidden", refreshAfterForbidden);
    return () => window.removeEventListener("permissions-forbidden", refreshAfterForbidden);
  }, [reloadPermissions]);

  const permissionSet = useMemo(() => new Set(currentUser?.permissions ?? []), [currentUser]);
  const value = useMemo<PermissionContextValue>(() => ({
    currentUser,
    loading,
    hasPermission: (code) => permissionSet.has(code),
    hasAllPermissions: (...codes) => codes.every((code) => permissionSet.has(code)),
    reloadPermissions,
    clearSession: () => setCurrentUser(null),
  }), [currentUser, loading, permissionSet, reloadPermissions]);

  return <PermissionContext.Provider value={value}>{children}</PermissionContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function usePermissions(): PermissionContextValue {
  const context = useContext(PermissionContext);
  if (!context) throw new Error("usePermissions must be used inside PermissionProvider");
  return context;
}
