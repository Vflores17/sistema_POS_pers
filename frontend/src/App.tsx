
import type { ReactElement } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import Clients from "./pages/Clients";
import Dashboard from "./pages/Dashboard";
import Login from "./pages/Login";
import Products from "./pages/Products";
import RouteSales from "./pages/RouteSales";
import Sales from "./pages/Sales";
import Users from "./pages/Users";
import PrivateRoute from "./routes/PrivateRoute";

export default function App(): ReactElement {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route element={<PrivateRoute />}>

        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/sales" element={<Sales />} />
        <Route path="/sales/new" element={<Sales />} />
        <Route path="/sales/:id/edit" element={<Sales />} />
        <Route path="/sales/:id/view" element={<Sales />} />
        <Route path="/route-sales" element={<RouteSales />} />
        <Route path="/route-sales/new" element={<RouteSales />} />
        <Route path="/route-sales/:id/edit" element={<RouteSales />} />
        <Route path="/route-sales/:id/view" element={<RouteSales />} />
        <Route path="/clients" element={<Clients />} />
        <Route path="/products" element={<Products />} />
        <Route path="/users" element={<Users />} />

      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />

    </Routes>
  );
}
