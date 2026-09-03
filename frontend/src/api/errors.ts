export interface BackendFieldError {
  field?: string;
  message?: string;
}

interface BackendErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: BackendFieldError[];
}

interface BackendErrorEnvelope {
  error?: BackendErrorBody;
  code?: string;
  message?: string;
  errors?: BackendFieldError[];
}

export const GLOBAL_ERROR_EVENT = "pos-global-error";

const CODE_MESSAGES: Record<string, string> = {
  AUTH_RATE_LIMITED: "Demasiados intentos. Intenta nuevamente en unos minutos.",
  ACCESS_DENIED: "No tienes permiso para realizar esta acción.",
  ADMIN_AUTHORIZATION_REJECTED: "La autorización administrativa no es válida o expiró.",
  AUTH_INVALID_CREDENTIALS: "Usuario o contraseña incorrectos.",
  AUTH_UNAUTHORIZED: "Tu sesión no es válida. Inicia sesión nuevamente.",
};

const BACKEND_MESSAGE_TRANSLATIONS: Record<string, string> = {
  "The last active administrator must be preserved": "No se puede bloquear, modificar o eliminar al último administrador activo.",
  "Essential ADMIN permissions cannot be removed": "No se pueden retirar los permisos esenciales del rol administrador.",
  "Essential ADMIN permissions cannot be deleted": "No se pueden eliminar permisos esenciales del administrador.",
};

export class ApiRequestError extends Error {
  readonly code: string;
  readonly status: number;
  readonly globallyReported: boolean;

  constructor(message: string, code: string, status: number, globallyReported: boolean) {
    super(message);
    this.name = "ApiRequestError";
    this.code = code;
    this.status = status;
    this.globallyReported = globallyReported;
  }
}

export async function apiErrorFromResponse(
  response: Response,
  fallback: string,
  report = true,
): Promise<ApiRequestError> {
  let envelope: BackendErrorEnvelope = {};
  try {
    envelope = (await response.json()) as BackendErrorEnvelope;
  } catch {
    envelope = {};
  }

  const body: BackendErrorBody = envelope.error ?? {
    code: envelope.code,
    message: envelope.message,
    fieldErrors: envelope.errors,
  };
  const code = body.code ?? "HTTP_ERROR";
  const fieldErrors = body.fieldErrors ?? [];
  const validationMessage = fieldErrors
    .map((item) => item.message?.trim())
    .filter((message): message is string => Boolean(message))
    .join("\n");
  const message = friendlyMessage(code, body.message, validationMessage, fallback);
  const shouldReport = report && code !== "ADMIN_AUTHORIZATION_REQUIRED";
  if (shouldReport) notifyGlobalError(message);
  return new ApiRequestError(message, code, response.status, shouldReport);
}

export async function parseApiResponse<T>(response: Response, fallback: string): Promise<T> {
  if (!response.ok) throw await apiErrorFromResponse(response, fallback);
  const body = (await response.json()) as { data: T };
  return body.data;
}

export async function requireApiSuccess(response: Response, fallback: string): Promise<void> {
  if (!response.ok) throw await apiErrorFromResponse(response, fallback);
}

export function reportNetworkError(fallback = "No fue posible conectar con el servidor."): ApiRequestError {
  notifyGlobalError(fallback);
  return new ApiRequestError(fallback, "NETWORK_ERROR", 0, true);
}

export function notifyGlobalError(message: string): void {
  window.dispatchEvent(new CustomEvent<string>(GLOBAL_ERROR_EVENT, { detail: message }));
}

export function isGloballyReportedError(error: unknown): boolean {
  return error instanceof ApiRequestError && error.globallyReported;
}

function friendlyMessage(
  code: string,
  backendMessage: string | undefined,
  validationMessage: string,
  fallback: string,
): string {
  if (code === "VALIDATION_ERROR" && validationMessage) return validationMessage;
  if (CODE_MESSAGES[code]) return CODE_MESSAGES[code];
  if (code === "INTERNAL_ERROR") return fallback;

  const translated = backendMessage ? BACKEND_MESSAGE_TRANSLATIONS[backendMessage.trim()] : undefined;
  if (translated) return translated;
  if (backendMessage && isSafeMessage(backendMessage)) return backendMessage.trim();
  return fallback;
}

function isSafeMessage(message: string): boolean {
  const value = message.trim();
  if (!value || value.length > 280) return false;
  return !/(exception|stack trace|\bjava\.|\borg\.|\bcom\.|sqlstate|select\s.+from|insert\s+into|delete\s+from|update\s+.+set|at\s+\S+\([^)]*\.java:\d+\)|[a-z]:\\|\/var\/|\/home\/)/i.test(value);
}
