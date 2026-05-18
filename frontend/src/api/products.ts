import { API_URL, buildHeaders, fetchWithAuth  } from "./http";

export type ProductStatus = "ACTIVE" | "INACTIVE";

export interface Product {
  id: string;
  name: string;
  description?: string;
  stock: number;
  price: number;
  status: ProductStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProductPayload {
  name: string;
  description?: string;
  stock: number;
  price: number;
  status: ProductStatus;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

interface PagedApiResponse<T> {
  success: boolean;
  data: T[];
  message?: string;
}


export async function listProducts(): Promise<Product[]> {
  const response = await fetchWithAuth(`${API_URL}/products`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load products");
  const json = (await response.json()) as PagedApiResponse<Product>;
  return json.data;
}

export async function createProduct(payload: ProductPayload): Promise<Product> {
  const response = await fetchWithAuth(`${API_URL}/products`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("Failed to create product");
  const json = (await response.json()) as ApiResponse<Product>;
  return json.data;
}

export async function updateProduct(id: string, payload: ProductPayload): Promise<Product> {
  const response = await fetchWithAuth(`${API_URL}/products/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("Failed to update product");
  const json = (await response.json()) as ApiResponse<Product>;
  return json.data;
}

export async function deleteProduct(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/products/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to delete product");
}

export async function createProductPrice(
  productId: string,
  type: "DETAIL" | "WHOLESALE" | "NEW",
  price: number
): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/products/${productId}/prices`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify({ type, price }),
  });
  if (!response.ok) throw new Error("Failed to create product price");
}

export async function getProductPrices(productId: string): Promise<{ type: string; price: number }[]> {
  const response = await fetchWithAuth(`${API_URL}/products/${productId}/prices`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load product prices");
  const json = await response.json();
  return json.data;
}