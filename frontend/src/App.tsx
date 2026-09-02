
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
        <Route element={<PrivateRoute permission="SALE_READ" />}><Route path="/sales" element={<Sales />} /><Route path="/sales/:id/view" element={<Sales />} /></Route>
        <Route element={<PrivateRoute permission="SALE_CREATE" />}><Route path="/sales/new" element={<Sales />} /></Route>
        <Route element={<PrivateRoute permission="SALE_READ" />}><Route path="/sales/:id/edit" element={<Sales />} /></Route>
        <Route element={<PrivateRoute permission="ROUTE_READ" />}><Route path="/route-sales" element={<RouteSales />} /><Route path="/route-sales/:id/view" element={<RouteSales />} /></Route>
        <Route element={<PrivateRoute permission="ROUTE_CREATE" />}><Route path="/route-sales/new" element={<RouteSales />} /></Route>
        <Route element={<PrivateRoute permission="ROUTE_READ" />}><Route path="/route-sales/:id/edit" element={<RouteSales />} /></Route>
        <Route element={<PrivateRoute permission="CLIENT_READ" />}><Route path="/clients" element={<Clients />} /></Route>
        <Route element={<PrivateRoute permission="PRODUCT_READ" />}><Route path="/products" element={<Products />} /></Route>
        <Route element={<PrivateRoute permission="USER_READ" />}><Route path="/users" element={<Users />} /></Route>
      </Route>

      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />

    </Routes>
  );
}
