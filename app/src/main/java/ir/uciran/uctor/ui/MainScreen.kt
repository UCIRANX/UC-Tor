package ir.uciran.uctor.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.uciran.uctor.BuildConfig
import ir.uciran.uctor.Phase
import ir.uciran.uctor.Prefs
import ir.uciran.uctor.PtSupport
import ir.uciran.uctor.R
import ir.uciran.uctor.TorController
import ir.uciran.uctor.Transport
import ir.uciran.uctor.UiState
import ir.uciran.uctor.MainActivity
import ir.uciran.uctor.AppChoices
import ir.uciran.uctor.AppChoice

private const val TG_CHANNEL = "UCIRANIR"
private const val TG_CHANNEL_URL = "https://t.me/UCIRANIR"

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val state by TorController.state.collectAsStateWithLifecycle()
    val prefs = remember { Prefs(ctx.applicationContext) }
    var transport by remember { mutableStateOf(Transport.fromId(prefs.transport)) }
    LaunchedEffect(state.phase) {
        if (state.phase == Phase.CONNECTED) (ctx as? MainActivity)?.startFullVpnIfReady()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF1C1238), Bg, Bg)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 190.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Header()
                    StatusBlock(state)
                    BridgeRefreshChip(state)
                    TransportCard(
                        selected = transport,
                        enabled = state.phase == Phase.IDLE || state.phase == Phase.ERROR,
                    ) { t ->
                        transport = t
                        prefs.transport = t.id
                    }
                    AnimatedVisibility(visible = state.phase == Phase.CONNECTED) { ProxyCard(state) }
                    ChannelCard()
                    AdvancedCard(prefs, state)
                    LogCard(state)
                    Text(
                        "UC Tor v${BuildConfig.VERSION_NAME}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)) {
                    ConnectButton(state) {
                        when (state.phase) {
                            Phase.CONNECTED, Phase.CONNECTING -> TorController.disconnect()
                            Phase.STOPPING -> Unit
                            else -> TorController.connect()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeRefreshChip(state: UiState) {
    val busy = state.bridgeSummary.contains("دریافت")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface2.copy(alpha=.75f)).clickable(enabled=!busy) { TorController.requestBridgeUpdate() }.padding(horizontal=14.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically) {
        Icon(Icons.Filled.Refresh, null, tint=Cyan, modifier=Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) { Text("تازه‌سازی بریج‌ها", fontWeight=FontWeight.Bold, fontSize=13.sp); Text("برای پیدا کردن مسیرهای جدید اتصال", fontSize=11.sp, color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(if (busy) "…" else "بروزرسانی", color=Cyan, fontSize=12.sp)
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Violet, Color(0xFF4F46E5), Cyan))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_onion_mono), null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("UC Tor", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(
                "اتصال امن و ساده به شبکه‌ی Tor",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectButton(state: UiState, onClick: () -> Unit) {
    val connecting = state.phase == Phase.CONNECTING || state.phase == Phase.STOPPING
    val connected = state.phase == Phase.CONNECTED
    val inf = rememberInfiniteTransition(label = "ring")
    val angle by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "angle",
    )
    val pulse by inf.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val ring = when {
        connected -> listOf(Green, Cyan, Green)
        state.phase == Phase.ERROR -> listOf(Color(0xFFF87171), Pink, Color(0xFFF87171))
        else -> listOf(Violet, Cyan, Pink, Violet)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .size(224.dp)
            .graphicsLayer {
                val s = if (connecting) pulse else 1f
                scaleX = s
                scaleY = s
            },
    ) {
        Box(
            Modifier
                .size(224.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(ring[0].copy(alpha = 0.35f), Color.Transparent)))
        )
        Box(
            Modifier
                .size(186.dp)
                .graphicsLayer { rotationZ = if (connecting) angle else 0f }
                .clip(CircleShape)
                .background(Brush.sweepGradient(ring))
        )
        Box(
            Modifier
                .size(172.dp)
                .clip(CircleShape)
                .background(Surface1)
                .clickable(enabled = state.phase != Phase.STOPPING, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_onion_mono), null,
                    tint = if (connected) Green else Color.White,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (state.phase) {
                        Phase.CONNECTED -> "قطع اتصال"
                        Phase.CONNECTING -> "لغو"
                        Phase.STOPPING -> "صبر کنید…"
                        else -> "اتصال"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusBlock(state: UiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val color = when (state.phase) {
            Phase.CONNECTED -> Green
            Phase.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onBackground
        }
        Text(state.status, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
        if (state.phase == Phase.CONNECTED && !state.vpnReady && state.vpnError.isBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("در حال فعال‌سازی VPN کل گوشی…", fontSize = 12.sp, color = Cyan)
        }
        if (state.vpnError.isNotBlank() && state.phase == Phase.CONNECTED) {
            Spacer(Modifier.height(4.dp))
            Text("VPN فعال نشد: ${state.vpnError}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        if (state.detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.detail, fontSize = 13.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.phase == Phase.CONNECTING) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = Cyan,
                trackColor = Surface2,
            )
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface1.copy(alpha = 0.94f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun TransportCard(selected: Transport, enabled: Boolean, onSelect: (Transport) -> Unit) {
    var open by remember { mutableStateOf(false) }
    GlassCard {
        Text("روش اتصال", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(selected.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Transport.entries.forEach { t ->
                    val ok = PtSupport.available(t.id)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(if (ok) t.label else "${t.label} (در این نسخه موجود نیست)")
                                Text(t.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            open = false
                            onSelect(t)
                        },
                        enabled = ok,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(selected.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProxyCard(state: UiState) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    GlassCard {
        Text("پروکسی محلی (برای تلگرام و برنامه‌های دیگر)", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LtrLine("SOCKS5", "127.0.0.1:${state.socksPort}")
        LtrLine("HTTP", "127.0.0.1:${state.httpPort}")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { openUrl(ctx, "tg://socks?server=127.0.0.1&port=${state.socksPort}") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) { Text("افزودن به تلگرام") }
            OutlinedButton(
                onClick = {
                    clip.setText(AnnotatedString("127.0.0.1:${state.socksPort}"))
                    toast(ctx, "کپی شد")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) { Text("کپی SOCKS") }
        }
    }
}

@Composable
private fun LtrLine(label: String, value: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.width(76.dp))
            Text(value, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ChannelCard() {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF229ED9), Color(0xFF6D28D9))))
            .clickable { openUrl(ctx, TG_CHANNEL_URL) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_telegram), null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("کانال تلگرام ما", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "@$TG_CHANNEL",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 15.sp,
                style = TextStyle(textDirection = TextDirection.Ltr),
            )
        }
        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = Color.White)
    }
}

@Composable
private fun AdvancedCard(prefs: Prefs, state: UiState) {
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(prefs.customBridges) }
    var exNodes by remember { mutableStateOf(prefs.excludeNodes) }
    var exExit by remember { mutableStateOf(prefs.excludeExitNodes) }
    var exitNodes by remember { mutableStateOf(prefs.exitNodes) }
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Settings, null, tint = Violet)
            Spacer(Modifier.width(8.dp))
            Text("تنظیمات پیشرفته و بریج‌ها", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                Text("مسیر عبور ترافیک", fontWeight = FontWeight.Bold)
                TunnelPicker(prefs)
                Text(
                    state.bridgeSummary, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { TorController.requestBridgeUpdate() }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text("به‌روزرسانی بریج‌ها")
                }
                Field("بریج‌های دستی (هر خط یک بریج)", custom, minLines = 3) {
                    custom = it
                    prefs.customBridges = it
                }
                Field("Exclude nodes   مثال: {ru},{cn}", exNodes) {
                    exNodes = it
                    prefs.excludeNodes = it
                }
                Field("Exclude exit nodes", exExit) {
                    exExit = it
                    prefs.excludeExitNodes = it
                }
                Field("Exit nodes   مثال: {de},{nl}", exitNodes) {
                    exitNodes = it
                    prefs.exitNodes = it
                }
                Text(
                    "تغییرات از اتصال بعدی اعمال می‌شوند.", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TunnelPicker(prefs: Prefs) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(prefs.tunnelMode) }
    var apps by remember { mutableStateOf(prefs.selectedApps) }
    var showApps by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha=.16f)).padding(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("کل گوشی", fontWeight=FontWeight.SemiBold); Text("همه‌ی برنامه‌ها از Tor", fontSize=11.sp, color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked=mode=="all", onCheckedChange={ if(it){mode="all"; prefs.tunnelMode="all"} })
        }
        Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Text("فقط برنامه‌های انتخابی", fontWeight=FontWeight.SemiBold); Text("بقیه‌ی برنامه‌ها مستقیم وصل می‌شوند", fontSize=11.sp, color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked=mode=="selected", onCheckedChange={ if(it){mode="selected"; prefs.tunnelMode="selected"}; showApps=it })
        }
        if (mode=="selected") {
            TextButton(onClick={showApps=!showApps}) { Text(if(showApps) "بستن فهرست برنامه‌ها" else "انتخاب برنامه‌ها (${apps.size})") }
            if(showApps) AppChoices.list(ctx).take(80).forEach { app ->
                Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth().clickable { apps=if(app.packageName in apps) apps-app.packageName else apps+app.packageName; prefs.selectedApps=apps }) {
                    Checkbox(checked=app.packageName in apps, onCheckedChange=null)
                    Text(app.label, maxLines=1, modifier=Modifier.weight(1f), fontSize=13.sp)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = minLines == 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(textDirection = TextDirection.Ltr, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun LogCard(state: UiState) {
    var open by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    GlassCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Text("گزارش اتصال (لاگ)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 10.dp)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                if (state.log.isEmpty()) "No log yet." else state.log.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    clip.setText(AnnotatedString(state.log.joinToString("\n")))
                    toast(ctx, "لاگ کپی شد")
                }) { Text("کپی لاگ") }
            }
        }
    }
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast(ctx, if (url.startsWith("tg:")) "تلگرام نصب نیست" else "برنامه‌ای برای باز کردن لینک پیدا نشد")
    }
}

private fun toast(ctx: Context, s: String) {
    Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
}
