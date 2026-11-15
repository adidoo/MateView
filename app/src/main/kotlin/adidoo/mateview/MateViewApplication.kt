// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview

import adidoo.mateview.data.ConnectionTester
import adidoo.mateview.data.DataAccess
import adidoo.mateview.data.QueryCache
import adidoo.mateview.data.repo.BatteryRepository
import adidoo.mateview.data.repo.ChargesRepository
import adidoo.mateview.data.repo.DrivesRepository
import adidoo.mateview.data.repo.OverviewRepository
import adidoo.mateview.data.repo.StatsRepository
import adidoo.mateview.data.settings.SecretStore
import adidoo.mateview.data.settings.SettingsStore
import adidoo.mateview.data.sql.SqlCatalog
import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
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
    val drives = DrivesRepository(dataAccess)
    val charges = ChargesRepository(dataAccess)
    val battery = BatteryRepository(dataAccess)
    val stats = StatsRepository(dataAccess)
}

class MateViewApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The OpenStreetMap tile servers ask every app to identify itself.
        // MapLibre must be initialised before it accepts an HTTP client.
        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(
            container.httpClient.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "MateView/${BuildConfig.VERSION_NAME} (+https://github.com/adidoo/MateView)")
                            .build(),
                    )
                }
                .build(),
        )
    }
}
