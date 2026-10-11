package ir.uciran.uctor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.uciran.uctor.PtSupport
import ir.uciran.uctor.Transport

/** دایره‌ی رنگی با حرف اول نام روش. */
@Composable
private fun TransportBadge(t: Transport, size: Int) {
    val c = typeColor(t.id)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(c.copy(alpha = .18f)).border(1.dp, c.copy(alpha = .5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(t.id.first().uppercase(), color = c, fontWeight = FontWeight.ExtraBold, fontSize = (size * 0.42f).sp) }
}

/** انتخاب روش اتصال: ردیف انتخاب‌شده + لیست بازشونده‌ی کارتی (به‌جای منوی کشویی). */
@Composable
internal fun TransportCard(selected: Transport, enabled: Boolean, onSelect: (Transport) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
    GlassCard(padding = 12.dp) {
        val head = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else .55f).clip(head)
                .background(typeColor(selected.id).copy(alpha = .10f))
                .border(1.dp, typeColor(selected.id).copy(alpha = .35f), head)
                .clickable(enabled = enabled) { open = !open }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportBadge(selected, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("روش اتصال", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(selected.desc, fontSize = 11.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.graphicsLayer { rotationZ = arrow })
        }
        AnimatedVisibility(visible = open && enabled) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Transport.entries.forEach { t ->
                    val ok = PtSupport.available(t.id)
                    val on = t == selected
                    val c = typeColor(t.id)
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().alpha(if (ok) 1f else .4f).clip(shape)
                            .background(if (on) c.copy(alpha = .12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = ok) { open = false; onSelect(t) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TransportBadge(t, 30)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (ok) t.label else "${t.label} (در این نسخه موجود نیست)", fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                            Text(t.desc, fontSize = 10.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on) Icon(Icons.Filled.Check, null, tint = c, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
