import { eventBus } from "./eventBus";

export interface RequestOptions extends Omit<RequestInit, "body"> {
  retries?: number;
  timeoutMs?: number;
  useCache?: boolean;
  body?: unknown;
  raw?: boolean;
}

export class HttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "HttpError";
  }
}

export interface RateLimit {
  limit: number;
  remaining: number;
  reset: number;
}

const MAX_CACHE_ENTRIES = 100;

/**
 * Cliente HTTP com retry exponencial (só para erros de rede e 5xx),
 * cache LRU em memória, timeout e token Bearer injetável.
 */
export class ApiClient {
  private baseUrl = "";
  private authToken: string | null = null;
  private cache = new Map<string, { data: unknown; expiresAt: number }>();
  readonly cacheTtlMs = 60_000;
  readonly maxRetries = 2;
  lastRateLimit: RateLimit | null = null;

  constructor(
    baseUrl = "",
    private backoffBaseMs = 500,
  ) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
  }

  setAuthToken(token: string | null): void {
    if (token !== this.authToken) this.cache.clear();
    this.authToken = token;
  }

  hasToken(): boolean {
    return Boolean(this.authToken);
  }

  async request<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
    const {
      retries = this.maxRetries,
      timeoutMs = 15_000,
      useCache = false,
      body,
      raw,
      ...fetchOpts
    } = options;
    const method = (fetchOpts.method ?? "GET").toUpperCase();
    const url = path.startsWith("http") ? path : `${this.baseUrl}${path}`;
    // O formato (raw vs JSON) e o Accept fazem parte da chave: o mesmo URL
    // devolve dados diferentes conforme o formato pedido.
    const accept = (fetchOpts.headers as Record<string, string> | undefined)?.["Accept"] ?? "json";
    const cacheKey = `${method}:${url}:${raw ? "raw" : "json"}:${accept}:${this.authToken ? "auth" : "anon"}`;

    if (useCache && method === "GET") {
      const entry = this.cache.get(cacheKey);
      if (entry && Date.now() < entry.expiresAt) {
        // Reinsere para manter a ordem LRU.
        this.cache.delete(cacheKey);
        this.cache.set(cacheKey, entry);
        return entry.data as T;
      }
      if (entry) this.cache.delete(cacheKey);
    }

    const headers: Record<string, string> = {
      Accept: "application/vnd.github+json",
      ...((fetchOpts.headers as Record<string, string>) ?? {}),
    };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (this.authToken) headers["Authorization"] = `Bearer ${this.authToken}`;

    let lastError: Error | null = null;

    for (let attempt = 0; attempt <= retries; attempt++) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), timeoutMs);
      let response: Response;
      try {
        response = await fetch(url, {
          ...fetchOpts,
          method,
          headers,
          body: body === undefined ? null : JSON.stringify(body),
          signal: controller.signal,
        });
      } catch (err) {
        clearTimeout(timer);
        lastError =
          err instanceof Error && err.name === "AbortError"
            ? new Error("O pedido demorou demasiado tempo.")
            : new Error("Sem ligação à internet ou servidor indisponível.");
        if (attempt < retries) {
          await this.backoff(attempt);
          continue;
        }
        break;
      }
      clearTimeout(timer);
      this.readRateLimit(response);

      if (!response.ok) {
        if (response.status >= 500 && attempt < retries) {
          await this.backoff(attempt);
          continue;
        }
        const error = new HttpError(response.status, await this.describe(response));
        eventBus.emit("api:error", { url, status: response.status, error: error.message });
        throw error;
      }

      const data = (
        raw ? await response.text() : response.status === 204 ? null : await response.json()
      ) as T;
      if (useCache && method === "GET") this.store(cacheKey, data);
      if (method !== "GET") this.cache.clear();
      eventBus.emit("api:success", { url, status: response.status });
      return data;
    }

    const error = lastError ?? new Error("Erro desconhecido");
    eventBus.emit("api:error", { url, error: error.message });
    throw error;
  }

  private store(key: string, data: unknown) {
    this.cache.set(key, { data, expiresAt: Date.now() + this.cacheTtlMs });
    while (this.cache.size > MAX_CACHE_ENTRIES) {
      const oldest = this.cache.keys().next().value;
      if (oldest === undefined) break;
      this.cache.delete(oldest);
    }
  }

  private readRateLimit(response: Response) {
    const limit = Number(response.headers.get("x-ratelimit-limit"));
    const remaining = Number(response.headers.get("x-ratelimit-remaining"));
    const reset = Number(response.headers.get("x-ratelimit-reset"));
    if (Number.isFinite(limit) && limit > 0) {
      this.lastRateLimit = { limit, remaining, reset: reset * 1000 };
      eventBus.emit("api:rate-limit", this.lastRateLimit);
    }
  }

  private async describe(response: Response): Promise<string> {
    let detail = "";
    try {
      const json = (await response.json()) as { message?: string };
      detail = json.message ?? "";
    } catch {
      /* corpo vazio */
    }
    if (response.status === 401) return "Sessão inválida ou expirada. Inicia sessão novamente.";
    if (response.status === 403 && response.headers.get("x-ratelimit-remaining") === "0")
      return "Limite de pedidos ao GitHub atingido. Inicia sessão ou espera alguns minutos.";
    if (response.status === 404) return "Não encontrado (ou sem permissão de acesso).";
    return detail ? `HTTP ${response.status}: ${detail}` : `HTTP ${response.status}`;
  }

  private backoff(attempt: number): Promise<void> {
    const delay = Math.min(this.backoffBaseMs * 2 ** attempt, 8000);
    return new Promise((r) => setTimeout(r, delay));
  }

  clearCache(): void {
    this.cache.clear();
  }

  cacheSize(): number {
    return this.cache.size;
  }
}

export const apiClient = new ApiClient("https://api.github.com");
