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
        const val ACTION_PAUSE = "ir.uciran.uctor.vpn.PAUSE"
        const val ACTION_RESUME = "ir.uciran.uctor.vpn.RESUME"
        private const val ID = 1002
        fun start(ctx: android.content.Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, FullVpnService::class.java).setAction(ACTION_START))
        fun stop(ctx: android.content.Context) = ctx.startService(Intent(ctx, FullVpnService::class.java).setAction(ACTION_STOP))
        fun togglePause(ctx: android.content.Context) = ctx.startService(Intent(ctx, FullVpnService::class.java).setAction(if (Prefs(ctx).vpnPaused) ACTION_RESUME else ACTION_PAUSE))
    }

    private var tun: ParcelFileDescriptor? = null
    private var running = false
    private var lastRx = android.net.TrafficStats.getTotalRxBytes()
    private var lastTx = android.net.TrafficStats.getTotalTxBytes()
    private val meter = android.os.Handler(android.os.Looper.getMainLooper())
    private val meterTick = object : Runnable { override fun run() { updateNotification(); meter.postDelayed(this, 1000) } }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTunnel(); return START_NOT_STICKY }
            ACTION_PAUSE -> { setPaused(true); return START_STICKY }
            ACTION_RESUME -> { setPaused(false); return START_STICKY }
        }
        foreground()
        meter.removeCallbacks(meterTick); meter.post(meterTick)
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
            if (p.tunnelMode == "selected") {
                p.selectedApps.forEach { runCatching { b.addAllowedApplication(it) } }
            } else {
                // Prevent the UC Tor process itself from looping into its own TUN.
                .addDisallowedApplication(packageName)
            }
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
        meter.removeCallbacks(meterTick)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() { if (running && !Prefs(this).vpnPaused) foreground() }

    private fun foreground() {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val n: Notification = NotificationCompat.Builder(this, UcTorApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_onion_mono)
            .setContentTitle("UC Tor")
            .setContentText(if (Prefs(this).vpnPaused) "موقتاً متوقف است" else "${TrafficMeter.text(speedRx())} ↓   ${TrafficMeter.text(speedTx())} ↑")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (Prefs(this).vpnPaused) "اتصال مجدد" else "توقف موقت", action(if (Prefs(this).vpnPaused) ACTION_RESUME else ACTION_PAUSE, 1))
            .addAction(0, "قطع کامل", action(ACTION_STOP, 2))
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ID, n, type)
    }

    private fun speedRx(): Long { val n=android.net.TrafficStats.getTotalRxBytes(); val d=(n-lastRx).coerceAtLeast(0); lastRx=n; return d }
    private fun speedTx(): Long { val n=android.net.TrafficStats.getTotalTxBytes(); val d=(n-lastTx).coerceAtLeast(0); lastTx=n; return d }
    private fun action(a: String, id: Int): PendingIntent = PendingIntent.getService(this, id, Intent(this, FullVpnService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun setPaused(value: Boolean) {
        Prefs(this).vpnPaused = value
        if (value) { runCatching { Tun2SocksBridge.stop() }; running = false } else if (!running) startTunnel()
        foreground()
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
