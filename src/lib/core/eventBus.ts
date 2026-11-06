type EventHandler<T = unknown> = (payload: T) => void;

/** Barramento de eventos simples para ligar módulos independentes. */
export class EventBus {
  private listeners = new Map<string, Set<EventHandler<any>>>();

  on<T = unknown>(event: string, handler: EventHandler<T>): () => void {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set());
    this.listeners.get(event)!.add(handler as EventHandler<any>);
    return () => this.off(event, handler);
  }

  off<T = unknown>(event: string, handler: EventHandler<T>): void {
    const handlers = this.listeners.get(event);
    handlers?.delete(handler as EventHandler<any>);
    if (handlers?.size === 0) this.listeners.delete(event);
  }

  emit<T = unknown>(event: string, payload?: T): void {
    for (const handler of [...(this.listeners.get(event) ?? [])]) {
      try {
        handler(payload as T);
      } catch (error) {
        queueMicrotask(() => {
          throw error;
        });
      }
    }
  }

  once<T = unknown>(event: string, handler: EventHandler<T>): () => void {
    const wrapped: EventHandler<T> = (payload) => {
      this.off(event, wrapped);
      handler(payload);
    };
    return this.on(event, wrapped);
  }

  clear(event?: string): void {
    if (event) this.listeners.delete(event);
    else this.listeners.clear();
  }
}

export const eventBus = new EventBus();
