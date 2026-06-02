import { API_URL, buildHeaders, fetchWithAuth } from "./http";

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

export async function listClients(): Promise<Client[]> {
  const response = await fetchWithAuth(`${API_URL}/clients?all=true`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load clients");
  const json = (await response.json()) as PagedApiResponse<Client>;
  return json.data.map((client) => ({
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
  if (!response.ok) throw new Error("Failed to create client");
  const json = (await response.json()) as ApiResponse<Client>;
  return { ...json.data, status: json.data.status ?? "ACTIVE" };
}

export async function updateClient(id: string, payload: ClientPayload): Promise<Client> {
  const response = await fetchWithAuth(`${API_URL}/clients/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("Failed to update client");
  const json = (await response.json()) as ApiResponse<Client>;
  return { ...json.data, status: json.data.status ?? "ACTIVE" };
}

export async function deleteClient(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/clients/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to delete client");
}