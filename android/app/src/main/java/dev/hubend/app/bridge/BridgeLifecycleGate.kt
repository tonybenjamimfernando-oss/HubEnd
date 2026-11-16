package dev.hubend.app.bridge

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Guarda de ciclo de vida da ponte nativa (AR-01).
 *
 * Estados explicitos: ACTIVE -> CLOSING -> DESTROYED. As transicoes sao idempotentes.
 *
 * Invariantes:
 *  - Em CLOSING/DESTROYED nao e aceite nenhum pedido novo.
 *  - Um resultado so e entregue a WebView enquanto o estado for ACTIVE, pelo que um callback
 *    tardio (por exemplo JsReplyProxy.postMessage depois de onDestroy) nunca chega a ponte destruida.
 *  - Nenhuma excecao de entrega atravessa esta guarda.
 *  - beginClosing()/seal() devolvem as chamadas pendentes uma unica vez, para serem rejeitadas
 *    de forma deterministica em vez de ficarem suspensas na interface.
 */
class BridgeLifecycleGate<T : Any> {
    enum class State { ACTIVE, CLOSING, DESTROYED }

    private val stateRef = AtomicReference(State.ACTIVE)
    private val pending: MutableSet<T> = ConcurrentHashMap.newKeySet<T>()

    fun state(): State = stateRef.get()

    fun isActive(): Boolean = stateRef.get() == State.ACTIVE

    fun pendingCount(): Int = pending.size

    /** Regista a chamada. Devolve false quando a ponte ja nao aceita trabalho. */
    fun track(call: T): Boolean {
        if (!isActive()) return false
        pending.add(call)
        if (!isActive()) {
            pending.remove(call)
            return false
        }
        return true
    }

    fun complete(call: T) {
        pending.remove(call)
    }

    /** Entrega o resultado apenas se a ponte estiver ativa. Devolve false quando o resultado e descartado. */
    fun deliver(call: T, action: (T) -> Unit): Boolean {
        pending.remove(call)
        if (!isActive()) return false
        return try {
            action(call)
            true
        } catch (throwable: Throwable) {
            false
        }
    }

    /** ACTIVE -> CLOSING. Devolve as chamadas pendentes uma unica vez. */
    fun beginClosing(): List<T> {
        if (!stateRef.compareAndSet(State.ACTIVE, State.CLOSING)) return emptyList()
        return pending.toList()
    }

    /** Fecha definitivamente e devolve o que ainda estiver pendente. */
    fun seal(): List<T> {
        stateRef.set(State.DESTROYED)
        val remaining = pending.toList()
        pending.clear()
        return remaining
    }
}