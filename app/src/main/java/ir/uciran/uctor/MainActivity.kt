package ir.uciran.uctor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import ir.uciran.uctor.ui.MainScreen
import ir.uciran.uctor.ui.UcTorTheme

class MainActivity : ComponentActivity() {
    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TorController.setVpnStatus(intent.getBooleanExtra(FullVpnService.EXTRA_READY, false), intent.getStringExtra(FullVpnService.EXTRA_ERROR).orEmpty())
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startFullVpnIfReady() {
        if (TorController.state.value.phase == Phase.CONNECTED && !Prefs(this).vpnRunning) VpnController.start(this)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(vpnReceiver) }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        VpnController.startAfterPermission(this, requestCode, resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = 0
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        TorController.init(applicationContext)
        ContextCompat.registerReceiver(this, vpnReceiver, IntentFilter(FullVpnService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { UcTorTheme { MainScreen() } }
    }
}
