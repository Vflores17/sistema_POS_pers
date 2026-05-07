const API_URL = "http://localhost:8080/api/v1";

export type UserStatus = "ACTIVE" | "BLOCKED" | "INACTIVE";

export interface RoleOption {
  id: string;
  name: string;
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

function getToken(): string {
  const token = localStorage.getItem("token");
  if (!token) {
    throw new Error("Missing auth token");
  }
  return token;
}

function buildHeaders(includeJson: boolean): HeadersInit {
  const token = getToken();
  return includeJson
    ? {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      }
    : {
        Authorization: `Bearer ${token}`,
      };
}

export async function listUsers(): Promise<User[]> {
  const response = await fetch(`${API_URL}/users`, {
    method: "GET",
    headers: buildHeaders(false),
  });

  if (!response.ok) {
    throw new Error("Failed to load users");
  }

  const json = (await response.json()) as PagedApiResponse<User>;
  return json.data;
}

export async function createUser(payload: CreateUserPayload): Promise<User> {
  const response = await fetch(`${API_URL}/users`, {
    method: "POST",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error("Failed to create user");
  }

  const json = (await response.json()) as ApiResponse<User>;
  return json.data;
}

export async function updateUser(id: string, payload: UpdateUserPayload): Promise<User> {
  const response = await fetch(`${API_URL}/users/${id}`, {
    method: "PUT",
    headers: buildHeaders(true),
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error("Failed to update user");
  }

  const json = (await response.json()) as ApiResponse<User>;
  return json.data;
}

export async function deleteUser(id: string): Promise<void> {
  const response = await fetch(`${API_URL}/users/${id}`, {
    method: "DELETE",
    headers: buildHeaders(false),
  });

  if (!response.ok) {
    throw new Error("Failed to delete user");
  }
}

export async function listRoles(): Promise<RoleOption[]> {
  const response = await fetch(`${API_URL}/roles`, {
    method: "GET",
    headers: buildHeaders(false),
  });

  if (!response.ok) {
    throw new Error("Failed to load roles");
  }

  const json = (await response.json()) as PagedApiResponse<RoleOption>;
  return json.data;
}
