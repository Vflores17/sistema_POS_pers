import { API_URL, buildHeaders, fetchWithAuth } from "./http";
import { executeWithAdminAuthorization } from "./admin-authorizations";
import { parseApiResponse, requireApiSuccess } from "./errors";

export type ClientType = "DETAIL" | "WHOLESALE" | "NEW";
export type ClientStatus = "ACTIVE" | "INACTIVE";

export interface Client {
  id: string;
  name: string;
  phone?: string | null;
  type: ClientType;
  status?: ClientStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface ClientPayload {
  name: string;
  phone?: string;
  type: ClientType;
  status: ClientStatus;
}

export async function listClients(): Promise<Client[]> {
  const response = await fetchWithAuth(`${API_URL}/clients?all=true`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  const clients = await parseApiResponse<Client[]>(response, "No se pudieron cargar los clientes.");
  return clients.map((client) => ({
    ...client,
    status: client.status ?? "ACTIVE",
  }));
}

export async function createClient(payload: ClientPayload): Promise<Client> {
  const response = await fetchWithAuth(`${API_URL}/clients`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  const client = await parseApiResponse<Client>(response, "No se pudo crear el cliente.");
  return { ...client, status: client.status ?? "ACTIVE" };
}

export async function updateClient(id: string, payload: ClientPayload): Promise<Client> {
  const response = await executeWithAdminAuthorization(
    { operationKey: "CLIENT_UPDATE", resourceType: "CLIENT", resourceId: id },
    (temporaryToken) => fetchWithAuth(`${API_URL}/clients/${id}`, {
      method: "PUT",
      headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
      body: JSON.stringify(payload),
    }),
  );
  const client = await parseApiResponse<Client>(response, "No se pudo actualizar el cliente.");
  return { ...client, status: client.status ?? "ACTIVE" };
}

export async function deleteClient(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/clients/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  await requireApiSuccess(response, "No se pudo eliminar el cliente.");
}
