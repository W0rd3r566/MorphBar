// Adapted from Islet by Dylan Neve (Apache-2.0) for MorphBar, 2026: package migration.
package dev.w0rd3r.morphbar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.w0rd3r.morphbar.ui.settings.HomeScreen
import dev.w0rd3r.morphbar.ui.theme.IsletTheme

class MainActivity : ComponentActivity() {

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The island runs in a foreground service, and a foreground service on
        // Android 13+ needs somewhere to put its (minimum-importance) notice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            IsletTheme {
                HomeScreen()
            }
        }
    }
}
