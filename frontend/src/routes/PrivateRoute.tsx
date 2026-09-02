import type { ReactElement } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { usePermissions } from "../auth/PermissionContext";

const TOKEN_KEY = "token";

export default function PrivateRoute({ permission }: { permission?: string }): ReactElement {
  const location = useLocation();
  const token = localStorage.getItem(TOKEN_KEY);
  const { loading, hasPermission } = usePermissions();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  if (loading) return <div>Cargando sesión...</div>;
  if (permission && !hasPermission(permission)) return <Navigate to="/dashboard" replace />;

  return <Outlet />;
}
