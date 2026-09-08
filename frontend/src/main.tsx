import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App";
import { PermissionProvider } from "./auth/PermissionContext";
import { SessionLockProvider } from "./auth/SessionLockContext";
import { AdminAuthorizationProvider } from "./auth/AdminAuthorizationContext";
import { GlobalErrorProvider } from "./auth/GlobalErrorContext";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <GlobalErrorProvider>
        <PermissionProvider><SessionLockProvider><AdminAuthorizationProvider><App /></AdminAuthorizationProvider></SessionLockProvider></PermissionProvider>
      </GlobalErrorProvider>
    </BrowserRouter>
  </StrictMode>,
);
