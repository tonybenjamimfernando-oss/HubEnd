package dev.hubend.app

import android.os.Bundle
import com.getcapacitor.BridgeActivity

/**
 * Controla o custo de herdar de BridgeActivity sem executar a inicialização
 * da classe base. Não cria Bridge nem WebView porque não chama super.onCreate.
 */
class CapacitorBridgeOnlyActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Intencionalmente não chamar super.onCreate(): sem Bridge/WebView.
    }
}
