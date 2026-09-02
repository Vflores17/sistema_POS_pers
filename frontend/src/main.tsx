import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App";
import { PermissionProvider } from "./auth/PermissionContext";
import { AdminAuthorizationProvider } from "./auth/AdminAuthorizationContext";
import { GlobalErrorProvider } from "./auth/GlobalErrorContext";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <GlobalErrorProvider>
        <PermissionProvider><AdminAuthorizationProvider><App /></AdminAuthorizationProvider></PermissionProvider>
      </GlobalErrorProvider>
    </BrowserRouter>
  </StrictMode>,
);
