import { API_URL, buildHeaders, fetchWithAuth  } from "./http";
import { parseApiResponse, requireApiSuccess } from "./errors";

export type UserStatus = "ACTIVE" | "BLOCKED" | "INACTIVE";

export interface RoleOption {
  id: string;
  name: string;
}

export interface Permission {
  id: string;
  code: string;
  module: string;
  description: string | null;
}

export type PermissionOverrideEffect = "ALLOW" | "DENY";

export interface UserPermissions {
  userId: string;
  inheritedPermissions: string[];
  allowedPermissions: string[];
  deniedPermissions: string[];
  effectivePermissions: string[];
}

export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: string[];
  permissions: string[];
}

export interface UserRole {
  id: string;
  name: string;
}

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  status: UserStatus;
  roles: UserRole[];
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateUserPayload {
  username: string;
  email: string;
  fullName: string;
  password: string;
  status: UserStatus;
  roleIds: string[];
}

export interface UpdateUserPayload {
  email: string;
  fullName: string;
  status: UserStatus;
  roleIds: string[];
}

export async function listUsers(): Promise<User[]> {
  const response = await fetchWithAuth(`${API_URL}/users`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<User[]>(response, "No se pudieron cargar los usuarios.");
}

export async function createUser(payload: CreateUserPayload): Promise<User> {
  const response = await fetchWithAuth(`${API_URL}/users`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<User>(response, "No se pudo crear el usuario.");
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<User> {
  const response = await fetchWithAuth(`${API_URL}/users/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  return parseApiResponse<User>(response, "No se pudo actualizar el usuario.");
}

export async function deleteUser(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/users/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  await requireApiSuccess(response, "No se pudo eliminar el usuario.");
}

export async function listRoles(): Promise<RoleOption[]> {
  const response = await fetchWithAuth(`${API_URL}/roles`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<RoleOption[]>(response, "No se pudieron cargar los roles.");
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await fetchWithAuth(`${API_URL}/auth/me`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<CurrentUser>(response, "No se pudo cargar la sesión actual.");
}

export async function listPermissions(): Promise<Permission[]> {
  const response = await fetchWithAuth(`${API_URL}/permissions?size=100&sort=module,asc`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<Permission[]>(response, "No se pudieron cargar los permisos.");
}

export async function getUserPermissions(userId: string): Promise<UserPermissions> {
  const response = await fetchWithAuth(`${API_URL}/users/${userId}/permissions`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  return parseApiResponse<UserPermissions>(response, "No se pudieron cargar los permisos del usuario.");
}

export async function replaceUserPermissionOverrides(
  userId: string,
  overrides: Array<{ permissionId: string; effect: PermissionOverrideEffect }>
): Promise<UserPermissions> {
  const response = await fetchWithAuth(`${API_URL}/users/${userId}/permission-overrides`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify({ overrides }),
  });
  return parseApiResponse<UserPermissions>(response, "No se pudieron actualizar los permisos individuales.");
}

export async function clearUserPermissionOverrides(userId: string): Promise<UserPermissions> {
  const response = await fetchWithAuth(`${API_URL}/users/${userId}/permission-overrides`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  return parseApiResponse<UserPermissions>(response, "No se pudieron restablecer los permisos individuales.");
}
