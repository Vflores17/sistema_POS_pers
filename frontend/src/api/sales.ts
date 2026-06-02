import { API_URL, buildHeaders, fetchWithAuth  } from "./http";

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
  method: PaymentMethod;
  amount: number;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}



async function parseResponse<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) {
    let message = fallback;
    try {
      const body = (await response.json()) as {
        error?: { message?: string };
        errors?: Array<{ message?: string }>;
      };
      if (body.error?.message) message = body.error.message;
      else if (body.errors?.[0]?.message) message = body.errors[0].message;
    } catch {
      message = fallback;
    }
    throw new Error(message);
  }
  const json = (await response.json()) as ApiResponse<T>;
  return json.data;
}

export async function listSales(): Promise<Sale[]> {
  const response = await fetchWithAuth(`${API_URL}/sales`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseResponse<Sale[]>(response, "Failed to load sales");
}

export async function getSaleById(id: string): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseResponse<Sale>(response, "Failed to load sale");
}

export async function getNextInvoiceNumber(): Promise<number> {
  const response = await fetchWithAuth(`${API_URL}/sales/next-invoice-number`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseResponse<number>(response, "Failed to get next invoice number");
}

export async function createSale(payload: CreateSalePayload): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseResponse<Sale>(response, "Failed to create sale");
}

export async function updateSale(id: string, payload: UpdateSalePayload): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseResponse<Sale>(response, "Failed to update sale");
}

export async function deleteSale(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to delete sale");
}

export async function changeSaleStatus(id: string, status: SaleStatus): Promise<Sale> {
  const response = await fetchWithAuth(`${API_URL}/sales/${id}/status`, {
    method: "PATCH",
    headers: buildHeaders(true),
    body: JSON.stringify({ status }),
  });
  return parseResponse<Sale>(response, "Failed to update sale status");
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
  return parseResponse<Sale>(response, "Failed to save sale payments");
}