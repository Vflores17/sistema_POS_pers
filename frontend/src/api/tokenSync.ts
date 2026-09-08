const CHANNEL_NAME = "pos-auth-sync";
const SYNC_STORAGE_KEY = "__pos_auth_sync_v1";
const TOKEN_GEN_KEY = "__pos_token_gen_v1";
const REFRESH_LOCK_KEY = "__pos_refresh_lock_v1";

const REFRESH_LOCK_WRITE_DELAY_MS = 15;
const REFRESH_LOCK_WAIT_MS = 3_000;

export type AuthSyncEvent =
  | { type: "auth:logged-out" }
  | { type: "auth:session-invalid"; gen: number }
  | { type: "auth:tokens-refreshed"; gen: number; expiresAt: number | null };

const storageChannel =
  typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(CHANNEL_NAME) : null;

const recentEventIds: string[] = [];

export function getTokenGeneration(): number {
  const value = Number(localStorage.getItem(TOKEN_GEN_KEY) ?? "0");
  return Number.isFinite(value) && value >= 0 ? Math.floor(value) : 0;
}

export function bumpTokenGeneration(): number {
  const next = getTokenGeneration() + 1;
  localStorage.setItem(TOKEN_GEN_KEY, String(next));
  return next;
}

export function broadcastAuthEvent(event: AuthSyncEvent): void {
  if (storageChannel !== null) {
    storageChannel.postMessage(event);
    return;
  }
  try {
    localStorage.setItem(SYNC_STORAGE_KEY, JSON.stringify({ id: crypto.randomUUID(), event }));
  } catch {
    return;
  }
}

export function subscribeToAuthEvents(handler: (event: AuthSyncEvent) => void): () => void {
  const onChannelMessage = (message: MessageEvent): void => {
    if (isAuthSyncEvent(message.data)) handler(message.data);
  };
  storageChannel?.addEventListener("message", onChannelMessage);

  const onStorage = (e: StorageEvent): void => {
    if (e.key !== SYNC_STORAGE_KEY || e.newValue === null) return;
    try {
      const parsed = JSON.parse(e.newValue) as { id?: unknown; event?: unknown };
      if (typeof parsed.id !== "string" || recentEventIds.includes(parsed.id)) return;
      recentEventIds.push(parsed.id);
      if (recentEventIds.length > 20) recentEventIds.shift();
      if (isAuthSyncEvent(parsed.event)) handler(parsed.event);
    } catch {
      return;
    }
  };
  window.addEventListener("storage", onStorage);

  return () => {
    storageChannel?.removeEventListener("message", onChannelMessage);
    window.removeEventListener("storage", onStorage);
  };
}

function isAuthSyncEvent(value: unknown): value is AuthSyncEvent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  if (candidate.type === "auth:logged-out") return true;
  if (candidate.type === "auth:session-invalid") return typeof candidate.gen === "number";
  if (candidate.type === "auth:tokens-refreshed") {
    return (
      typeof candidate.gen === "number" &&
      (typeof candidate.expiresAt === "number" || candidate.expiresAt === null)
    );
  }
  return false;
}

export async function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== "undefined" && typeof navigator.locks !== "undefined") {
    return navigator.locks.request("pos-auth-refresh", () => task());
  }
  const id = crypto.randomUUID();
  if (await acquireFallbackLock(id)) {
    try {
      return await task();
    } finally {
      releaseFallbackLock(id);
    }
  }
  await delay(REFRESH_LOCK_WAIT_MS);
  return task();
}

function acquireFallbackLock(id: string): Promise<boolean> {
  localStorage.setItem(REFRESH_LOCK_KEY, JSON.stringify({ id, at: Date.now() }));
  return delay(REFRESH_LOCK_WRITE_DELAY_MS).then(() => {
    const holder = JSON.parse(localStorage.getItem(REFRESH_LOCK_KEY) ?? "null") as {
      id?: string;
    } | null;
    return holder?.id === id;
  });
}

function releaseFallbackLock(id: string): void {
  const holder = JSON.parse(localStorage.getItem(REFRESH_LOCK_KEY) ?? "null") as {
    id?: string;
  } | null;
  if (holder?.id === id) localStorage.removeItem(REFRESH_LOCK_KEY);
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}