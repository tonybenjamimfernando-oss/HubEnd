package dev.hubops.omega

/**
 * HubEnd 0.1.0 — correção de compilação do HubOps 1.29.0.
 *
 * `java.lang.Process.pid()` (Java 9) não existe na API Android, pelo que o código original
 * não compilava. Em Android o PID está no campo privado `pid` da implementação libcore
 * (`java.lang.ProcessImpl` / `UNIXProcess`). Lemos esse campo por reflexão; se a plataforma
 * o bloquear, devolvemos null e os chamadores falham de forma fechada (sem monitorização de
 * memória não se executa o runtime), em vez de ignorarem o limite de RSS em silêncio.
 */
object ProcessIds {
    fun pidOrNull(process: Process): Long? = runCatching {
        var type: Class<*>? = process.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == "pid" }
            if (field != null) {
                field.isAccessible = true
                val value = (field.get(process) as Number).toLong()
                return@runCatching if (value > 0) value else null
            }
            type = type.superclass
        }
        null
    }.getOrNull()
}
