import type { ReactElement } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";

const TOKEN_KEY = "token";

export default function PrivateRoute(): ReactElement {
  const location = useLocation();
  const token = localStorage.getItem(TOKEN_KEY);

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}
