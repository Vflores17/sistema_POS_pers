import { ApiRequestError, reportNetworkError } from "./errors";

const API_URL = "http://localhost:8080/api/v1";

export { API_URL };

export function buildHeaders(includeJson: boolean): HeadersInit {
  const token = localStorage.getItem("token") ?? "";
  return includeJson
    ? {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      }
    : {
        Authorization: `Bearer ${token}`,
      };
}

async function refreshToken(): Promise<boolean> {
  const refreshToken = localStorage.getItem("refreshToken");
  if (!refreshToken) return false;

  try {
    const response = await fetchWithAuth(`${API_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;

    const json = await response.json();

    localStorage.setItem("token", json.data.accessToken);
    localStorage.setItem("refreshToken", json.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

function redirectToLogin(): never {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  window.location.href = "/login";
  throw new Error("Sesión expirada.");
}

export async function fetchWithAuth(
  url: string,
  options: RequestInit,
  config: { recoverUnauthorized?: boolean; reportNetworkFailure?: boolean } = {},
): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(url, options);
  } catch {
    if (config.reportNetworkFailure !== false) throw reportNetworkError();
    throw new ApiRequestError("No fue posible conectar con el servidor.", "NETWORK_ERROR", 0, false);
  }

  if (response.status === 401 && config.recoverUnauthorized !== false) {
    const refreshed = await refreshToken();
    if (!refreshed) redirectToLogin();

    const newOptions = {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${localStorage.getItem("token")}`,
      },
    };
    try {
      response = await fetch(url, newOptions);
    } catch {
      throw reportNetworkError();
    }

    if (response.status === 401) redirectToLogin();
  }

  if (response.status === 403) {
    const code = await response.clone().json()
      .then((body: { error?: { code?: string } }) => body.error?.code ?? "")
      .catch(() => "");
    if (code !== "ADMIN_AUTHORIZATION_REQUIRED" && code !== "ADMIN_AUTHORIZATION_REJECTED") {
      window.dispatchEvent(new Event("permissions-forbidden"));
    }
  }

  return response;
}
