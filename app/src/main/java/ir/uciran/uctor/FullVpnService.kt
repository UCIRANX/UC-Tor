package ir.uciran.uctor

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.File

/** Routes the whole device through the local Tor SOCKS listener. */
class FullVpnService : VpnService() {
    companion object {
        const val ACTION_START = "ir.uciran.uctor.vpn.START"
        const val ACTION_STOP = "ir.uciran.uctor.vpn.STOP"
        private const val ID = 1002
        fun start(ctx: android.content.Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, FullVpnService::class.java).setAction(ACTION_START))
        fun stop(ctx: android.content.Context) = ctx.startService(Intent(ctx, FullVpnService::class.java).setAction(ACTION_STOP))
    }

    private var tun: ParcelFileDescriptor? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        foreground()
        if (!running) startTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        val p = Prefs(this)
        if (p.socksPort <= 0) return
        try {
            val b = Builder()
                .setSession("UC Tor")
                .setMtu(1500)
                .addAddress("198.18.0.1", 15)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:uctor::1", 64)
                .addRoute("::", 0)
                .addDnsServer("10.0.0.1")
                // Prevent the UC Tor process itself from looping into its own TUN.
                .addDisallowedApplication(packageName)
            tun = b.establish() ?: error("Android VPN interface could not be established")
            val fd = tun!!.detachFd()
            val cfg = File(filesDir, "hev-tun.conf")
            cfg.writeText("""
                TUN:
                  NAME: tun0
                  MTU: 1500
                LOG:
                  LEVEL: warn
                SOCKS5:
                  ADDR: 127.0.0.1
                  PORT: ${p.socksPort}
                  UDP_MODE: udp
                DNS:
                  ADDR: 10.0.0.1
                  PORT: 53
            """.trimIndent())
            if (!Tun2SocksBridge.start(cfg, fd)) error("tun2socks native library is not available")
            running = true
            Prefs(this).vpnRunning = true
        } catch (t: Throwable) {
            tun?.close(); tun = null
            Prefs(this).vpnRunning = false
            File(filesDir, "vpn.state").writeText("error:${t.message ?: t.javaClass.simpleName}")
            stopSelf()
        }
    }

    private fun stopTunnel() {
        runCatching { Tun2SocksBridge.stop() }
        tun?.close(); tun = null
        running = false
        Prefs(this).vpnRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun foreground() {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val n: Notification = NotificationCompat.Builder(this, UcTorApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onion_mono)
            .setContentTitle("UC Tor")
            .setContentText("کل ترافیک گوشی از Tor عبور می‌کند")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID, n, type)
    }
}

/** Reflection keeps the Kotlin source buildable before the GitHub Action downloads the AAR. */
object Tun2SocksBridge {
    private var cls: Class<*>? = null
    private var service: Any? = null
    private fun load(): Class<*>? = runCatching { Class.forName("hev.htproxy.TProxyService") }.getOrNull()
    fun start(config: File, fd: Int): Boolean = runCatching {
        val c = load() ?: return false
        val m = c.getMethod("TProxyStartService", String::class.java, Integer.TYPE)
        val ok = m.invoke(null, config.absolutePath, fd) as? Boolean ?: false
        if (ok) { cls = c; service = c }
        ok
    }.getOrDefault(false)
    fun stop() { runCatching { cls?.getMethod("TProxyStopService")?.invoke(null) }; cls = null; service = null }
}
