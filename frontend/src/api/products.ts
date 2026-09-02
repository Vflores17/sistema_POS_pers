import { API_URL, buildHeaders, fetchWithAuth  } from "./http";
import { executeWithAdminAuthorization } from "./admin-authorizations";
import { parseApiResponse, requireApiSuccess } from "./errors";

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

export async function listProducts(): Promise<Product[]> {
  const response = await fetchWithAuth(`${API_URL}/products?all=true`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Product[]>(response, "No se pudieron cargar los productos.");
}

export async function createProduct(payload: ProductPayload): Promise<Product> {
  const response = await fetchWithAuth(`${API_URL}/products`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<Product>(response, "No se pudo crear el producto.");
}

export async function updateProduct(id: string, payload: ProductPayload): Promise<Product> {
  const response = await executeWithAdminAuthorization(
    { operationKey: "PRODUCT_UPDATE", resourceType: "PRODUCT", resourceId: id },
    (temporaryToken) => fetchWithAuth(`${API_URL}/products/${id}`, {
      method: "PUT",
      headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
      body: JSON.stringify(payload),
    }),
  );
  return parseApiResponse<Product>(response, "No se pudo actualizar el producto.");
}

export async function deleteProduct(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/products/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  await requireApiSuccess(response, "No se pudo eliminar el producto.");
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
  await requireApiSuccess(response, "No se pudo crear el precio del producto.");
}

export async function getProductPrices(productId: string): Promise<{ id: string; type: string; price: number }[]> {
  const response = await fetchWithAuth(`${API_URL}/products/${productId}/prices`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<{ id: string; type: string; price: number }[]>(
    response,
    "No se pudieron cargar los precios del producto.",
  );
}

export async function updateProductPrice(
  productId: string,
  priceId: string,
  type: "DETAIL" | "WHOLESALE" | "NEW",
  price: number
): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/products/${productId}/prices/${priceId}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify({ type, price }),
  });
  await requireApiSuccess(response, "No se pudo actualizar el precio del producto.");
}

export async function getAllProductPrices(): Promise<Record<string, { id: string; type: string; price: number }[]>> {
  const response = await fetchWithAuth(`${API_URL}/products/prices/all`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Record<string, { id: string; type: string; price: number }[]>>(
    response,
    "No se pudieron cargar los precios de los productos.",
  );
}
