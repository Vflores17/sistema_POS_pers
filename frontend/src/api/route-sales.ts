import { API_URL, buildHeaders, fetchWithAuth  } from "./http";
import { executeWithAdminAuthorization } from "./admin-authorizations";
import { parseApiResponse, requireApiSuccess } from "./errors";

export type RoutePaymentMethod = "CASH" | "SINPE" | "TRANSFER" | "CARD";
export type RouteSaleStatus = "PENDING" | "PARTIAL" | "PAID" | "CANCELLED";
export interface RouteSaleItemPayload {
  productId: string;
  quantity: number;
  price?: number;
}

export interface CreateRouteSalePayload {
  clientId: string;
  driverId: string;
  paymentMethod: RoutePaymentMethod;
  items: RouteSaleItemPayload[];
  comments?: string;
}

export type UpdateRouteSalePayload = CreateRouteSalePayload;

export interface RouteSaleDetail {
  id?: string;
  productId: string;
  productName: string;
  quantity: number;
  price: number;
  subtotal: number;
}

export interface RouteSalePayment {
  id: string;
  saleId: string;
  routeSaleId: string;
  method: RoutePaymentMethod;
  amount: number;
  createdAt?: string;
}

export interface RouteSale {
  id: string;
  invoiceNumber: number;
  invoiceLabel?: string;
  userId: string;
  clientId: string;
  driverId: string;
  paymentMethod: RoutePaymentMethod;
  total: number;
  status: RouteSaleStatus;
  comments?: string;
  createdAt: string;
  details: RouteSaleDetail[];
  payments: RouteSalePayment[];
  
}

export interface CreateRouteSalePaymentPayload {
  id?: string;
  method: RoutePaymentMethod;
  amount: number;
}

export interface RouteSalePaymentMovement extends RouteSalePayment {
  invoiceNumber: number;
  clientId: string;
  routeSaleCreatedAt: string;
  createdAt: string;
}

export async function listRouteSales(): Promise<RouteSale[]> {
  const response = await fetchWithAuth(`${API_URL}/route-sales`, { method: "GET", headers: buildHeaders(false) });
  return parseApiResponse<RouteSale[]>(response, "No se pudieron cargar las rutas.");
}

export async function listRouteSalePaymentMovements(from: string, to: string): Promise<RouteSalePaymentMovement[]> {
  const params = new URLSearchParams({ from, to });
  const response = await fetchWithAuth(`${API_URL}/route-sales/payments?${params.toString()}`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<RouteSalePaymentMovement[]>(response, "No se pudieron cargar los movimientos de caja de rutas.");
}

export async function getRouteSaleById(id: string): Promise<RouteSale> {
  const response = await fetchWithAuth(`${API_URL}/route-sales/${id}`, { method: "GET", headers: buildHeaders(false) });
  return parseApiResponse<RouteSale>(response, "No se pudo cargar la ruta.");
}

export async function getNextRouteSaleInvoiceNumber(): Promise<number> {
  const response = await fetchWithAuth(`${API_URL}/route-sales/next-invoice-number`, { method: "GET", headers: buildHeaders(false) });
  return parseApiResponse<number>(response, "No se pudo obtener el próximo número de ruta.");
}

export async function createRouteSale(payload: CreateRouteSalePayload): Promise<RouteSale> {
  const response = await fetchWithAuth(`${API_URL}/route-sales`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<RouteSale>(response, "No se pudo crear la ruta.");
}

export async function updateRouteSale(id: string, payload: UpdateRouteSalePayload): Promise<RouteSale> {
  const response = await executeWithAdminAuthorization(
    { operationKey: "ROUTE_UPDATE", resourceType: "ROUTE", resourceId: id },
    (temporaryToken) => fetchWithAuth(`${API_URL}/route-sales/${id}`, {
      method: "PUT",
      headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
      body: JSON.stringify(payload),
    }),
  );
  return parseApiResponse<RouteSale>(response, "No se pudo actualizar la ruta.");
}

export async function deleteRouteSale(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/route-sales/${id}`, { method: "DELETE", headers: buildHeaders(false) });
  await requireApiSuccess(response, "No se pudo eliminar la ruta.");
}

export async function saveRouteSalePayments(
  routeSaleId: string,
  payments: CreateRouteSalePaymentPayload[]
): Promise<RouteSale> {
  const response = await fetchWithAuth(`${API_URL}/route-sales/${routeSaleId}/payments`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payments),
  });
  return parseApiResponse<RouteSale>(response, "No se pudieron guardar los pagos de la ruta.");
}

export async function changeRouteSaleStatus(id: string, status: RouteSaleStatus): Promise<RouteSale> {
  const response = await fetchWithAuth(`${API_URL}/route-sales/${id}/status`, {
    method: "PATCH",
    headers: buildHeaders(true),
    body: JSON.stringify({ status }),
  });
  return parseApiResponse<RouteSale>(response, "No se pudo actualizar el estado de la ruta.");
}
