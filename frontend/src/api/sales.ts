import { API_URL, buildHeaders, fetchWithAuth  } from "./http";
import { executeWithAdminAuthorization } from "./admin-authorizations";
import { parseApiResponse, requireApiSuccess } from "./errors";

export type PaymentMethod = "CASH" | "SINPE" | "TRANSFER" | "CARD";
export type SaleStatus = "PENDING" | "PARTIAL" | "PAID" | "CANCELLED";

export interface SaleItemPayload {
  productId: string;
  quantity: number;
  price?: number;
}

export interface CreateSalePayload {
  clientId: string;
  paymentMethod: PaymentMethod;
  items: SaleItemPayload[];
  status?: string;
  comments?: string;
}

export type UpdateSalePayload = CreateSalePayload;

export interface SaleDetail {
  id?: string;
  productId: string;
  productName: string;
  quantity: number;
  price: number;
  subtotal: number;
}

export interface Sale {
  id: string;
  invoiceNumber: number;
  total: number;
  clientId: string;
  paymentMethod: PaymentMethod;
  status: SaleStatus;
  createdAt: string;
  details: SaleDetail[];
  payments: SalePayment[];
  comments?: string;
}

export interface SalePayment {
  id: string;
  saleId: string;
  method: PaymentMethod;
  amount: number;
  createdAt?: string;
}

export interface CreateSalePaymentPayload {
  id?: string;
  method: PaymentMethod;
  amount: number;
}

export interface SalePaymentMovement extends SalePayment {
  invoiceNumber: number;
  clientId: string;
  saleCreatedAt: string;
  createdAt: string;
}

export async function listSales(): Promise<Sale[]> {
  const response = await fetchWithAuth(`${API_URL}/sales`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Sale[]>(response, "No se pudieron cargar las ventas.");
}

export async function listSalePaymentMovements(from: string, to: string): Promise<SalePaymentMovement[]> {
  const params = new URLSearchParams({ from, to });
  const response = await fetchWithAuth(`${API_URL}/sales/payments?${params.toString()}`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<SalePaymentMovement[]>(response, "No se pudieron cargar los movimientos de caja.");
}

export async function getSaleById(id: string): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Sale>(response, "No se pudo cargar la venta.");
}

export async function getNextInvoiceNumber(): Promise<number> {
  const response = await fetchWithAuth(`${API_URL}/sales/next-invoice-number`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<number>(response, "No se pudo obtener el próximo número de factura.");
}

export async function createSale(payload: CreateSalePayload): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<Sale>(response, "No se pudo crear la venta.");
}

export async function updateSale(id: string, payload: UpdateSalePayload): Promise<Sale> {
  const response = await executeWithAdminAuthorization(
    { operationKey: "SALE_UPDATE", resourceType: "SALE", resourceId: id },
    (temporaryToken) => fetchWithAuth(`${API_URL}/sales/${id}`, {
      method: "PUT",
      headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
      body: JSON.stringify(payload),
    }),
  );
  return parseApiResponse<Sale>(response, "No se pudo actualizar la venta.");
}

export async function deleteSale(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  await requireApiSuccess(response, "No se pudo eliminar la venta.");
}

export async function changeSaleStatus(id: string, status: SaleStatus): Promise<Sale> {
  const request = (temporaryToken?: string): Promise<Response> => fetchWithAuth(`${API_URL}/sales/${id}/status`, {
    method: "PATCH",
    headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
    body: JSON.stringify({ status }),
  });
  const response = status === "CANCELLED"
    ? await executeWithAdminAuthorization(
        { operationKey: "SALE_CANCEL", resourceType: "SALE", resourceId: id },
        request,
      )
    : await request();
  return parseApiResponse<Sale>(response, "No se pudo actualizar el estado de la venta.");
}

export async function savePayments(
  saleId: string,
  payments: CreateSalePaymentPayload[]
): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales/${saleId}/payments`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payments),
  });
  return parseApiResponse<Sale>(response, "No se pudieron guardar los pagos de la venta.");
}
