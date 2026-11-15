// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview

import adidoo.mateview.ui.AppLanguage
import adidoo.mateview.ui.MateViewApp
import adidoo.mateview.ui.theme.MateViewTheme
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MateViewTheme {
                MateViewApp()
            }
        }
    }
}
