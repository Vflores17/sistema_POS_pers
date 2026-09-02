import { API_URL, buildHeaders, fetchWithAuth  } from "./http";

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


export async function listUsers(): Promise<User[]> {
  const response = await fetchWithAuth(`${API_URL}/users`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load users");
  const json = (await response.json()) as PagedApiResponse<User>;
  return json.data;
}

export async function createUser(payload: CreateUserPayload): Promise<User> {
  const response = await fetchWithAuth(`${API_URL}/users`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("Failed to create user");
  const json = (await response.json()) as ApiResponse<User>;
  return json.data;
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<User> {
  const response = await fetchWithAuth(`${API_URL}/users/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });
  if (!response.ok) throw new Error("Failed to update user");
  const json = (await response.json()) as ApiResponse<User>;
  return json.data;
}

export async function deleteUser(id: string): Promise<void> {
  const response = await fetchWithAuth(`${API_URL}/users/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to delete user");
}

export async function listRoles(): Promise<RoleOption[]> {
  const response = await fetchWithAuth(`${API_URL}/roles`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load roles");
  const json = (await response.json()) as PagedApiResponse<RoleOption>;
  return json.data;
}

export async function getCurrentUser(): Promise<CurrentUser> {
  const response = await fetchWithAuth(`${API_URL}/auth/me`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load current user");
  const json = (await response.json()) as ApiResponse<CurrentUser>;
  return json.data;
}

export async function listPermissions(): Promise<Permission[]> {
  const response = await fetchWithAuth(`${API_URL}/permissions?size=100&sort=module,asc`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load permissions");
  const json = (await response.json()) as PagedApiResponse<Permission>;
  return json.data;
}

export async function getUserPermissions(userId: string): Promise<UserPermissions> {
  const response = await fetchWithAuth(`${API_URL}/users/${userId}/permissions`, {
    method: "GET",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to load user permissions");
  const json = (await response.json()) as ApiResponse<UserPermissions>;
  return json.data;
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
  if (!response.ok) throw new Error("Failed to update permission overrides");
  const json = (await response.json()) as ApiResponse<UserPermissions>;
  return json.data;
}

export async function clearUserPermissionOverrides(userId: string): Promise<UserPermissions> {
  const response = await fetchWithAuth(`${API_URL}/users/${userId}/permission-overrides`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });
  if (!response.ok) throw new Error("Failed to clear permission overrides");
  const json = (await response.json()) as ApiResponse<UserPermissions>;
  return json.data;
}
