import { eventBus } from "./eventBus";

/**
 * Armazenamento local do dispositivo (HybridApp; espelho SQLite nativo previsto na Fase 1).
 * Cada "tabela" é guardada como JSON no localStorage e emite `db:changed`.
 */
const PREFIX = "hubend:";

function isBrowser() {
  return typeof window !== "undefined" && typeof window.localStorage !== "undefined";
}

export function readTable<T>(name: string, fallback: T): T {
  if (!isBrowser()) return fallback;
  try {
    const raw = window.localStorage.getItem(PREFIX + name);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function writeTable<T>(name: string, value: T): void {
  if (!isBrowser()) return;
  try {
    window.localStorage.setItem(PREFIX + name, JSON.stringify(value));
  } catch {
    /* quota cheia: ignora */
  }
  eventBus.emit("db:changed", { table: name });
}

export function removeTable(name: string): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(PREFIX + name);
  eventBus.emit("db:changed", { table: name });
}

export function clearAll(): void {
  if (!isBrowser()) return;
  for (const key of Object.keys(window.localStorage)) {
    if (key.startsWith(PREFIX)) window.localStorage.removeItem(key);
  }
  window.sessionStorage.removeItem(PREFIX + "token");
  eventBus.emit("db:changed", { table: "*" });
}

// ---- Atividade local (antiga tabela notifications) ----
export interface Activity {
  id: string;
  title: string;
  body?: string | undefined;
  timestamp: number;
  read: boolean;
  source?: "local" | "github" | "push" | undefined;
}

const ACTIVITY_LIMIT = 200;

export function listActivity(): Activity[] {
  return readTable<Activity[]>("activity", []);
}

export function addActivity(title: string, body?: string, source: Activity["source"] = "local"): Activity {
  const item: Activity = {
    id: `act_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    title,
    body,
    timestamp: Date.now(),
    read: false,
    source,
  };
  writeTable("activity", [item, ...listActivity()].slice(0, ACTIVITY_LIMIT));
  eventBus.emit("ui:notification-badge-update", { count: 1 });
  return item;
}

export function markActivityRead(): void {
  writeTable(
    "activity",
    listActivity().map((a) => ({ ...a, read: true })),
  );
  eventBus.emit("ui:notification-badge-reset");
}

export function clearActivity(): void {
  writeTable("activity", []);
  eventBus.emit("ui:notification-badge-reset");
}

// ---- Token GitHub ----
const TOKEN_KEY = PREFIX + "token";

export function loadToken(): string | null {
  if (!isBrowser()) return null;
  return window.localStorage.getItem(TOKEN_KEY) ?? window.sessionStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string, remember: boolean): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.sessionStorage.removeItem(TOKEN_KEY);
  (remember ? window.localStorage : window.sessionStorage).setItem(TOKEN_KEY, token);
}

export function forgetToken(): void {
  if (!isBrowser()) return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.sessionStorage.removeItem(TOKEN_KEY);
}
