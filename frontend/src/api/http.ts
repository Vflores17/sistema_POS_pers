import { ApiRequestError, reportNetworkError } from "./errors";
import {
  broadcastAuthEvent,
  bumpTokenGeneration,
  getTokenGeneration,
  subscribeToAuthEvents,
  withRefreshLock,
} from "./tokenSync";

const API_URL = "http://localhost:8080/api/v1";

const ACCESS_TOKEN_KEY = "token";
const REFRESH_TOKEN_KEY = "refreshToken";
const ACCESS_EXPIRES_AT_KEY = "accessExpiresAt";

const PROACTIVE_REFRESH_THRESHOLD_MS = 60_000;
const PROACTIVE_RETRY_MS = 60_000;

type RefreshResult = { ok: true } | { ok: false; reason: "rejected" | "network" };

let refreshInFlight: Promise<RefreshResult> | null = null;
let proactiveTimer: number | null = null;

export { API_URL };

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) ?? "";
}

function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) ?? "";
}

function getAccessExpiresAt(): number | null {
  const stored = localStorage.getItem(ACCESS_EXPIRES_AT_KEY);
  if (stored === null) return null;
  const value = Number(stored);
  return Number.isFinite(value) && value > 0 ? value : null;
}

export function persistTokens(
  accessToken: string,
  refreshToken: string,
  expiresInSeconds?: number,
): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  writeAccessExpiry(expiresInSeconds, accessToken);
  const gen = bumpTokenGeneration();
  scheduleProactiveRefresh();
  broadcastAuthEvent({
    type: "auth:tokens-refreshed",
    gen,
    expiresAt: getAccessExpiresAt(),
  });
}

export function clearStoredAuth(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(ACCESS_EXPIRES_AT_KEY);
  stopProactiveRefresh();
}

function writeAccessExpiry(expiresInSeconds: number | undefined, accessToken: string): void {
  if (typeof expiresInSeconds === "number" && expiresInSeconds > 0) {
    localStorage.setItem(ACCESS_EXPIRES_AT_KEY, String(Date.now() + expiresInSeconds * 1000));
    return;
  }
  const exp = expFromJwt(accessToken);
  if (exp !== null) {
    localStorage.setItem(ACCESS_EXPIRES_AT_KEY, String(exp));
    return;
  }
  localStorage.removeItem(ACCESS_EXPIRES_AT_KEY);
}

function expFromJwt(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1])) as { exp?: unknown };
    return typeof payload.exp === "number" ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

function scheduleProactiveRefresh(): void {
  stopProactiveRefresh();
  const expiresAt = getAccessExpiresAt();
  if (expiresAt === null || !getRefreshToken()) return;
  const delay = Math.max(0, expiresAt - Date.now() - PROACTIVE_REFRESH_THRESHOLD_MS);
  proactiveTimer = window.setTimeout(() => {
    proactiveTimer = null;
    void runProactiveRefresh();
  }, delay);
}

function stopProactiveRefresh(): void {
  if (proactiveTimer !== null) {
    window.clearTimeout(proactiveTimer);
    proactiveTimer = null;
  }
}

function runProactiveRefresh(): void {
  void refreshTokens().then((result) => {
    if (result.ok) return;
    if (result.reason === "rejected") {
      forceLoginRedirect();
      return;
    }
    proactiveTimer = window.setTimeout(() => {
      proactiveTimer = null;
      void runProactiveRefresh();
    }, PROACTIVE_RETRY_MS);
  });
}

export function refreshTokens(): Promise<RefreshResult> {
  const capturedRefreshToken = getRefreshToken();
  if (!capturedRefreshToken) return Promise.resolve({ ok: false, reason: "rejected" });
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async (): Promise<RefreshResult> => {
    try {
      return await withRefreshLock(async (): Promise<RefreshResult> => {
        if (getRefreshToken() !== capturedRefreshToken) return { ok: true };

        let response: Response;
        try {
          response = await fetch(`${API_URL}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: capturedRefreshToken }),
          });
        } catch {
          return { ok: false, reason: "network" };
        }

        if (!response.ok) return rejectAsInvalid(capturedRefreshToken);

        const json = (await response.json()) as {
          data?: { accessToken?: string; refreshToken?: string; expiresIn?: number };
        };
        const accessToken = json.data?.accessToken;
        const nextRefreshToken = json.data?.refreshToken;
        if (!accessToken || !nextRefreshToken) return rejectAsInvalid(capturedRefreshToken);

        persistTokens(accessToken, nextRefreshToken, json.data?.expiresIn);
        return { ok: true };
      });
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

function rejectAsInvalid(capturedRefreshToken: string): RefreshResult {
  if (getRefreshToken() !== capturedRefreshToken) {
    return getRefreshToken() ? { ok: true } : { ok: false, reason: "rejected" };
  }
  clearStoredAuth();
  broadcastAuthEvent({ type: "auth:session-invalid", gen: getTokenGeneration() });
  return { ok: false, reason: "rejected" };
}

export function performLogout(): Promise<void> {
  const refreshToken = getRefreshToken();
  const request = refreshToken
    ? fetch(`${API_URL}/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => undefined)
    : Promise.resolve(undefined);
  return request.then(() => {
    clearStoredAuth();
    broadcastAuthEvent({ type: "auth:logged-out" });
  });
}

subscribeToAuthEvents((event) => {
  if (event.type === "auth:tokens-refreshed") {
    if (event.gen < getTokenGeneration()) return;
    scheduleProactiveRefresh();
    return;
  }
  if (event.type === "auth:session-invalid" && getTokenGeneration() > event.gen) return;
  clearStoredAuth();
  if (!(getAccessToken() === "" && getRefreshToken() === "" && window.location.pathname === "/login")) {
    window.location.href = "/login";
  }
});

function forceLoginRedirect(): void {
  clearStoredAuth();
  window.location.href = "/login";
}

function redirectToLogin(): never {
  forceLoginRedirect();
  throw new Error("Sesión expirada.");
}

export function buildHeaders(includeJson: boolean): HeadersInit {
  const token = getAccessToken();
  return includeJson
    ? {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      }
    : {
        Authorization: `Bearer ${token}`,
      };
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
    const result = await refreshTokens();
    if (!result.ok) redirectToLogin();

    const newOptions = {
      ...options,
      headers: {
        ...options.headers,
        Authorization: `Bearer ${getAccessToken()}`,
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