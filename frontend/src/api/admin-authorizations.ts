import { API_URL, buildHeaders, fetchWithAuth } from "./http";
import { ApiRequestError, apiErrorFromResponse, notifyGlobalError } from "./errors";

export type AdminOperationKey =
  | "SALE_UPDATE"
  | "SALE_CANCEL"
  | "CLIENT_UPDATE"
  | "PRODUCT_UPDATE"
  | "ROUTE_UPDATE"
  | "DRIVER_UPDATE";

export type AdminResourceType = "SALE" | "CLIENT" | "PRODUCT" | "ROUTE" | "DRIVER";

export interface AdminAuthorizationTarget {
  operationKey: AdminOperationKey;
  resourceType: AdminResourceType;
  resourceId: string;
}

export interface AdminCredentials {
  username: string;
  password: string;
}

interface ApiResponse<T> {
  data: T;
}

interface AuthorizationResponse {
  token: string;
}

interface ApiErrorBody {
  error?: {
    code?: string;
    message?: string;
  };
}

export class AdminAuthorizationCancelledError extends Error {
  constructor() {
    super("Administrator authorization was cancelled");
    this.name = "AdminAuthorizationCancelledError";
  }
}

type AuthorizationPrompt = (target: AdminAuthorizationTarget) => Promise<string | null>;

let authorizationPrompt: AuthorizationPrompt | null = null;

export function registerAdminAuthorizationPrompt(prompt: AuthorizationPrompt | null): void {
  authorizationPrompt = prompt;
}

export async function issueAdminAuthorization(
  target: AdminAuthorizationTarget,
  credentials: AdminCredentials,
): Promise<string> {
  const response = await fetchWithAuth(
    `${API_URL}/admin-authorizations`,
    {
      method: "POST",
      headers: buildHeaders(true),
      body: JSON.stringify({
        adminUsername: credentials.username,
        adminPassword: credentials.password,
        operationKey: target.operationKey,
        resourceType: target.resourceType,
        resourceId: target.resourceId,
      }),
    },
    { recoverUnauthorized: false, reportNetworkFailure: false },
  );
  if (!response.ok) {
    throw await apiErrorFromResponse(
      response,
      "Usuario o contraseña de administrador incorrectos.",
      false,
    );
  }
  const body = await readBody(response);
  return (body as ApiResponse<AuthorizationResponse>).data.token;
}

export async function executeWithAdminAuthorization(
  target: AdminAuthorizationTarget,
  request: (temporaryToken?: string) => Promise<Response>,
): Promise<Response> {
  const initialResponse = await request();
  if (initialResponse.status !== 403) return initialResponse;

  const initialError = await readBody(initialResponse.clone());
  if (initialError.error?.code !== "ADMIN_AUTHORIZATION_REQUIRED") return initialResponse;
  if (!authorizationPrompt) throw new Error("El diálogo de autorización no está disponible.");

  const temporaryToken = await authorizationPrompt(target);
  if (!temporaryToken) throw new AdminAuthorizationCancelledError();

  const retryResponse = await request(temporaryToken);
  if (retryResponse.status === 403) {
    const retryError = await readBody(retryResponse.clone());
    if (retryError.error?.code === "ADMIN_AUTHORIZATION_REJECTED") {
      const message = "La autorización administrativa no es válida o expiró.";
      notifyGlobalError(message);
      throw new ApiRequestError(message, "ADMIN_AUTHORIZATION_REJECTED", 403, true);
    }
  }
  return retryResponse;
}

export function isAdminAuthorizationCancelled(error: unknown): boolean {
  return error instanceof AdminAuthorizationCancelledError;
}

async function readBody(response: Response): Promise<ApiErrorBody> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return {};
  }
}
