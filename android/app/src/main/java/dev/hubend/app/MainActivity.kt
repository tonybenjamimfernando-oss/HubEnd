package dev.hubend.app

import android.os.Bundle
import com.getcapacitor.BridgeActivity
import dev.hubend.app.bridge.HubEndEnginesPlugin

/** Ponto de entrada do HubEnd: WebView Capacitor com a ponte para os motores HubOps registada. */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(HubEndEnginesPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
