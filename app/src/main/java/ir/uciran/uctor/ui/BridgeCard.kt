package ir.uciran.uctor.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.uciran.uctor.TorController
import ir.uciran.uctor.UiState

private fun typeColor(t: String) = when (t) {
    "webtunnel" -> Cyan
    "obfs4" -> Violet
    "snowflake" -> Green
    "meek_lite" -> Pink
    else -> Color(0xFF94A3B8)
}

private fun typeName(t: String) = if (t == "meek_lite") "meek" else t

/** کارت بریج‌ها: تعداد هر نوع به‌صورت چیپ رنگی + دکمه‌ی به‌روزرسانی با وضعیت. */
@Composable
internal fun BridgeCard(state: UiState) {
    val total = state.bridgeCounts.sumOf { it.second }
    val updated = state.bridgeUpdated.trim()
    val sub = if (updated.isBlank()) "لیست داخلی برنامه · $total بریج"
    else "آخرین به‌روزرسانی ${updated.replace('T', ' ').removeSuffix("Z").take(16)} UTC · $total بریج"
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a",
    )
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("بریج‌ها", fontWeight = FontWeight.Bold)
                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Cyan.copy(alpha = .14f))
                    .clickable(enabled = !state.bridgeBusy) { TorController.requestBridgeUpdate() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Cyan, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (state.bridgeBusy) spin else 0f })
                Spacer(Modifier.width(6.dp))
                Text(if (state.bridgeBusy) "در حال دریافت…" else "به‌روزرسانی", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (state.bridgeCounts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            state.bridgeCounts.chunked(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (t, n) -> BridgeChip(t, n, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (state.bridgeFailed) {
            Spacer(Modifier.height(8.dp))
            Text("دریافت ناموفق بود؛ لیست قبلی استفاده می‌شود", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BridgeChip(type: String, count: Int, modifier: Modifier) {
    val c = typeColor(type)
    val a = if (count > 0) 1f else .45f
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape).background(c.copy(alpha = .12f * a)).border(1.dp, c.copy(alpha = .35f * a), shape).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", color = c.copy(alpha = a), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(typeName(type), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

