import { API_URL, buildHeaders, fetchWithAuth } from "./http";
import { executeWithAdminAuthorization } from "./admin-authorizations";
import { parseApiResponse, requireApiSuccess } from "./errors";

export type DriverStatus = "ACTIVE" | "INACTIVE";

export interface Driver {
  id: string;
  name: string;
  status: DriverStatus;
  createdAt: string;
}

export interface CreateDriverPayload {
  name: string;
  status: DriverStatus;
}

export interface UpdateDriverPayload {
  name: string;
  status: DriverStatus;
}

export async function listDrivers(): Promise<Driver[]> {
  const response = await fetchWithAuth(`${API_URL}/drivers`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Driver[]>(response, "No se pudieron cargar los choferes.");
}

export async function createDriver(payload: CreateDriverPayload): Promise<Driver> {
  const response = await fetchWithAuth(`${API_URL}/drivers`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<Driver>(response, "No se pudo crear el chofer.");
}

export async function updateDriver(id: string, payload: UpdateDriverPayload): Promise<Driver> {
  const response = await executeWithAdminAuthorization(
    { operationKey: "DRIVER_UPDATE", resourceType: "DRIVER", resourceId: id },
    (temporaryToken) => fetchWithAuth(`${API_URL}/drivers/${id}`, {
      method: "PUT",
      headers: { ...buildHeaders(true), ...(temporaryToken ? { "X-Admin-Authorization": temporaryToken } : {}) },
      body: JSON.stringify(payload),
    }),
  );
  return parseApiResponse<Driver>(response, "No se pudo actualizar el chofer.");
}

export async function deleteDriver(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/drivers/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  await requireApiSuccess(response, "No se pudo eliminar el chofer.");
}
