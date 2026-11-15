// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview

import adidoo.mateview.data.ConnectionTester
import adidoo.mateview.data.settings.SecretStore
import adidoo.mateview.data.settings.SettingsStore
import android.app.Application
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-written dependency container: one instance of each long-lived object. */
class AppContainer(application: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val settings = SettingsStore(application, SecretStore())
    val connectionTester = ConnectionTester(httpClient)
}

class MateViewApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
