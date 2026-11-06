package dev.hubops

import android.app.Application
import com.hubops.vislumbrops.core.ServiceLocator
import dev.hubops.omega.OmegaEngineSuite

/** Single application lifecycle shared by every HubOps engine. */
class HubOpsApplication : Application() {
    lateinit var omegaEngines: OmegaEngineSuite
        private set

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.bootstrap(this)
        omegaEngines = OmegaEngineSuite(this)
    }
}
