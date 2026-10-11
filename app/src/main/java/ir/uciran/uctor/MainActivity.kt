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
import androidx.core.content.ContextCompat
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
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

    // Android's VPN approval must be shown before the notification permission.
    private val vpnPermission =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                requestNotificationPermission()
                // بعد از تأیید مجوز، اگر Tor وصل است VPN را همین‌جا شروع کن.
                if (TorController.state.value.phase == Phase.CONNECTED && !Prefs(this).vpnRunning) FullVpnService.start(this)
            }
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestVpnPermissionFirst() {
        val intent = VpnController.permissionIntent(this)
        if (intent != null) vpnPermission.launch(intent) else requestNotificationPermission()
    }

    fun startFullVpnIfReady() {
        if (TorController.state.value.phase != Phase.CONNECTED || Prefs(this).vpnRunning) return
        val intent = VpnController.permissionIntent(this)
        if (intent != null) vpnPermission.launch(intent) else FullVpnService.start(this)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(vpnReceiver) }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = 0
        TorController.init(applicationContext)
        ContextCompat.registerReceiver(this, vpnReceiver, IntentFilter(FullVpnService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { UcTorTheme { MainScreen() } }
        window.decorView.post { requestVpnPermissionFirst() }
    }
}
