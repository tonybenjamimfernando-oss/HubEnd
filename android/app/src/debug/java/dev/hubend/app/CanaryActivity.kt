package dev.hubend.app

import android.app.Activity

/**
 * Activity Android mínima para validar o ciclo de vida do emulador sem BridgeActivity,
 * Capacitor ou WebView. Existe apenas na variante debug usada pelos testes instrumentados.
 */
class CanaryActivity : Activity()
