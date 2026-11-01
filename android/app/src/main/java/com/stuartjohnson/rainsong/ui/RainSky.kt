package com.stuartjohnson.rainsong.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.stuartjohnson.rainsong.protocol.DropEvent
import kotlin.math.min
import kotlin.math.sin

private class RainParticle(
    var x: Float,
    var y: Float,
    var vy: Float,
    var vx: Float,
    var len: Float,
    var thick: Float,
    var alpha: Float,
    var hit: Boolean,
)

private class Cloud(
    val baseX: Float,
    val baseY: Float,
    val scale: Float,
    val phase: Float,
)

@Composable
fun RainSky(
    intensityDps: Float,
    lastDrop: DropEvent?,
    dropCount: Int,
    connected: Boolean,
    onCloudTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drops = remember { ArrayList<RainParticle>(256) }
    val clouds = remember {
        listOf(
            Cloud(0.28f, 0.16f, 1.15f, 0.2f),
            Cloud(0.62f, 0.12f, 1.45f, 1.1f),
            Cloud(0.82f, 0.20f, 0.85f, 2.4f),
        )
    }
    var frame by remember { mutableStateOf(0L) }
    var lastNs by remember { mutableStateOf(0L) }
    var ambient by remember { mutableStateOf(0f) }
    var lastSeenCount by remember { mutableStateOf(0) }

    LaunchedEffect(dropCount, lastDrop) {
        if (dropCount > lastSeenCount && lastDrop != null) {
            lastSeenCount = dropCount
            spawn(drops, lastDrop.strength, hit = true)
        } else {
            lastSeenCount = dropCount
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { ns ->
                val dt = if (lastNs == 0L) 0.016f else ((ns - lastNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNs = ns
                val rain = if (connected) intensityDps else 0f
                ambient += rain * dt
                while (ambient >= 1f && drops.size < 220) {
                    ambient -= 1f
                    spawn(drops, strength = 18_000, hit = false)
                }
                if (rain <= 0.05f) ambient = 0f
                tick(drops, dt, rain)
                frame = ns
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(onCloudTap) {
            detectTapGestures { offset ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (clouds.any { hitsCloud(it, offset.x, offset.y, w, h) }) {
                    onCloudTap()
                }
            }
        },
    ) {
        frame // recompose
        val t = frame / 1_000_000_000f
        drawSky(intensityDps, connected)
        clouds.forEach { drawCloud(it, t, intensityDps) }
        drops.forEach { drawDrop(it) }
    }
}

private fun hitsCloud(cloud: Cloud, x: Float, y: Float, w: Float, h: Float): Boolean {
    val cx = cloud.baseX * w
    val cy = cloud.baseY * h
    val s = cloud.scale * min(w, h) * 0.18f
    val dx = (x - cx) / (s * 1.7f)
    val dy = (y - cy) / (s * 1.0f)
    return dx * dx + dy * dy <= 1f
}

private fun spawn(drops: MutableList<RainParticle>, strength: Int, hit: Boolean) {
    val g = (strength.coerceIn(0, 65535) / 65535f)
    val x = (Math.random() * 0.92 + 0.04).toFloat()
    drops.add(
        RainParticle(
            x = x,
            y = if (hit) 0.18f + (Math.random() * 0.06).toFloat() else (-0.08 + Math.random() * 0.12).toFloat(),
            vy = 0.55f + g * 0.85f + (if (hit) 0.15f else 0f),
            vx = 0.04f + (Math.random() * 0.05).toFloat(),
            len = if (hit) 0.055f + g * 0.06f else 0.028f + g * 0.03f,
            thick = if (hit) 3.2f + g * 3.5f else 1.4f + g * 1.6f,
            alpha = if (hit) 0.92f else 0.28f + g * 0.25f,
            hit = hit,
        ),
    )
}

private fun tick(drops: MutableList<RainParticle>, dt: Float, rain: Float) {
    val it = drops.listIterator()
    while (it.hasNext()) {
        val d = it.next()
        d.y += d.vy * dt
        d.x += d.vx * dt * 0.15f
        if (!d.hit) d.alpha *= (1f - 0.08f * dt)
        if (d.y > 1.12f || d.alpha < 0.04f) it.remove()
    }
    if (rain < 0.2f && drops.size > 8) {
        // Let the sky clear when the rain stops.
        val extra = drops.size - 8
        repeat(min(extra, 3)) {
            if (drops.isNotEmpty() && !drops.first().hit) drops.removeAt(0)
        }
    }
}

private fun DrawScope.drawSky(dps: Float, connected: Boolean) {
    val wet = (dps / 10f).coerceIn(0f, 1f)
    val top = if (connected) Color(0xFF071821) else Color(0xFF0A1216)
    val mid = Color(0xFF0C2A38).copy(alpha = 0.95f)
    val bot = Color(0xFF0A3A3A).copy(red = 0.04f + 0.06f * wet, green = 0.16f + 0.10f * wet)
    drawRect(
        brush = Brush.verticalGradient(listOf(top, mid, bot)),
        size = size,
    )
}

private fun DrawScope.drawCloud(cloud: Cloud, t: Float, dps: Float) {
    val drift = sin(t * 0.12f + cloud.phase) * size.width * 0.03f
    val cx = cloud.baseX * size.width + drift
    val cy = cloud.baseY * size.height
    val s = cloud.scale * min(size.width, size.height) * 0.18f
    val glow = (0.55f + 0.25f * (dps / 8f).coerceIn(0f, 1f))
    val body = Color(0xFFD7EEF2).copy(alpha = 0.22f + 0.10f * glow)
    val core = Color(0xFFF4FBFC).copy(alpha = 0.18f + 0.12f * glow)
    fun puff(dx: Float, dy: Float, rx: Float, ry: Float, color: Color) {
        drawOval(
            color = color,
            topLeft = Offset(cx + dx * s - rx * s, cy + dy * s - ry * s),
            size = Size(rx * 2f * s, ry * 2f * s),
        )
    }
    puff(0f, 0.05f, 1.15f, 0.42f, body)
    puff(-0.55f, 0.12f, 0.72f, 0.36f, body)
    puff(0.58f, 0.14f, 0.70f, 0.34f, body)
    puff(-0.15f, -0.22f, 0.62f, 0.38f, core)
    puff(0.28f, -0.12f, 0.55f, 0.32f, core)
}

private fun DrawScope.drawDrop(d: RainParticle) {
    val x = d.x * size.width
    val y = d.y * size.height
    val len = d.len * size.height
    val color = if (d.hit) Color(0xFFB8F3FF) else Color(0xFF7FDBD4)
    drawLine(
        color = color.copy(alpha = d.alpha),
        start = Offset(x, y),
        end = Offset(x + d.vx * size.width * 0.04f, y + len),
        strokeWidth = d.thick,
        cap = StrokeCap.Round,
    )
        if (d.hit) {
        drawCircle(
            color = color.copy(alpha = d.alpha * 0.35f),
            radius = d.thick * 1.6f,
            center = Offset(x, y),
        )
    }
}
