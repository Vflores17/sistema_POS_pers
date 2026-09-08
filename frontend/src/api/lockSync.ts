const CHANNEL_NAME = "pos-lock-sync";
const SYNC_STORAGE_KEY = "__pos_lock_sync_v1";
export const LAST_ACTIVITY_KEY = "__pos_lock_last_activity_at";

export type LockSyncEvent =
  | { type: "lock:blocked"; at: number }
  | { type: "lock:unlocked"; at: number };

const storageChannel =
  typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(CHANNEL_NAME) : null;

const recentEventIds: string[] = [];

export function readSharedLastActivity(): number {
  const value = Number(localStorage.getItem(LAST_ACTIVITY_KEY) ?? "0");
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function writeSharedLastActivity(at: number): number {
  const current = readSharedLastActivity();
  const next = Math.max(current, at);
  try {
    localStorage.setItem(LAST_ACTIVITY_KEY, String(next));
  } catch {
    return current;
  }
  return next;
}

export function broadcastLockEvent(event: LockSyncEvent): void {
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

export function subscribeToLockEvents(handler: (event: LockSyncEvent) => void): () => void {
  const onChannelMessage = (message: MessageEvent): void => {
    if (isLockSyncEvent(message.data)) handler(message.data);
  };
  storageChannel?.addEventListener("message", onChannelMessage);

  const onStorage = (event: StorageEvent): void => {
    if (event.key !== SYNC_STORAGE_KEY || event.newValue === null) return;
    try {
      const parsed = JSON.parse(event.newValue) as { id?: unknown; event?: unknown };
      if (typeof parsed.id !== "string" || recentEventIds.includes(parsed.id)) return;
      recentEventIds.push(parsed.id);
      if (recentEventIds.length > 20) recentEventIds.shift();
      if (isLockSyncEvent(parsed.event)) handler(parsed.event);
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

function isLockSyncEvent(value: unknown): value is LockSyncEvent {
  if (typeof value !== "object" || value === null) return false;
  const candidate = value as Record<string, unknown>;
  if (candidate.type !== "lock:blocked" && candidate.type !== "lock:unlocked") return false;
  return typeof candidate.at === "number";
}