// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview

import adidoo.mateview.data.ConnectionTester
import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.QueryCache
import adidoo.mateview.data.repo.OverviewRepository
import adidoo.mateview.data.settings.SecretStore
import adidoo.mateview.data.settings.SettingsStore
import adidoo.mateview.data.sql.SqlCatalog
import android.app.Application
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Hand-written dependency container: one instance of each long-lived object. */
class AppContainer(application: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val settings = SettingsStore(application, SecretStore())
    val connectionTester = ConnectionTester(httpClient)
    val queryCache = QueryCache(File(application.cacheDir, "queries"))
    val dataAccess = DataAccess(
        settings = settings,
        tester = connectionTester,
        catalog = SqlCatalog { path -> application.assets.open(path).bufferedReader().use { it.readText() } },
        cache = queryCache,
    )
    val overview = OverviewRepository(dataAccess)
}

class MateViewApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
