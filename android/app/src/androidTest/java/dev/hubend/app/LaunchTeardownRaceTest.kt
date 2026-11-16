package dev.hubend.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getcapacitor.Bridge
import com.getcapacitor.BridgeActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.FixMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Isola a hipótese de corrida entre o fecho do ActivityScenario e callbacks nativos
 * ainda pendentes na ponte JavaScript/WebView do Capacitor.
 *
 * A ordem alfabética é intencional: A1, A2 e A3 têm de correr na mesma execução,
 * nesta sequência, para que os marcadores TEARDOWN possam ser comparados.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LaunchTeardownRaceTest {

    @Test
    fun launchAndCloseImmediately() {
        repeat(10) { i ->
            Log.i("TEARDOWN", "A1_iter_$i: START")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
            Log.i("TEARDOWN", "A1_iter_$i: CLOSED")
            Thread.sleep(100)
        }
    }

    @Test
    fun launchWaitThenClose() {
        repeat(10) { i ->
            Log.i("TEARDOWN", "A2_iter_$i: START")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                Thread.sleep(2_000)
            }
            Log.i("TEARDOWN", "A2_iter_$i: CLOSED")
        }
    }

    @Test
    fun launchWithExplicitWebViewCleanup() {
        repeat(10) { i ->
            Log.i("TEARDOWN", "A3_iter_$i: START")
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                val bridgeField = BridgeActivity::class.java
                    .getDeclaredField("bridge")
                    .apply { isAccessible = true }
                val bridge = bridgeField.get(activity) as? Bridge
                val webView = bridge?.webView
                webView?.post {
                    webView.evaluateJavascript("1") { }
                }
            }
            Thread.sleep(1_500)
            scenario.close()
            Log.i("TEARDOWN", "A3_iter_$i: CLOSED")
        }
    }
}
