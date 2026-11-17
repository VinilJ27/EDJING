package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.theme.DeckSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VoidBlack
import com.example.viewmodel.DeckUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class JogTouchZone {
    NONE,
    SCRATCH,
    PITCH_BEND
}

@Composable
fun TurntableView(
    deck: DeckUiState,
    accentColor: Color,
    vinylRpm: Int,
    onScratchStart: () -> Unit,
    onScratchDelta: (Float, Long) -> Unit,
    onScratchEnd: () -> Unit,
    onPitchBend: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestDeck by rememberUpdatedState(deck)
    val latestVinylRpm by rememberUpdatedState(vinylRpm)
    val latestOnScratchStart by rememberUpdatedState(onScratchStart)
    val latestOnScratchDelta by rememberUpdatedState(onScratchDelta)
    val latestOnScratchEnd by rememberUpdatedState(onScratchEnd)
    val latestOnPitchBend by rememberUpdatedState(onPitchBend)

    val scope = rememberCoroutineScope()
    var activeZone by remember { mutableStateOf(JogTouchZone.NONE) }
    var inertiaJob by remember { mutableStateOf<Job?>(null) }

    // 33.3 RPM = 200 deg/sec, 45 RPM = 270 deg/sec
    val degPerMs = if (vinylRpm == 45) 0.27f else 0.20f
    val autoRotation = (deck.cursorMs * degPerMs * (1f + deck.pitchOffset + deck.pitchBend)) % 360f
    val displayRotation = if (deck.isScratching) {
        deck.scratchAngleDeg
    } else {
        (autoRotation + deck.scratchAngleDeg) % 360f
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (deck.isPlaying) 24f else 4f,
        animationSpec = tween(durationMillis = 320),
        label = "tonearm"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val platterSize = min(maxWidth, maxHeight) * 0.94f

        Box(
            modifier = Modifier
                .size(platterSize)
                .testTag("deck_${if (deck.deckIndex == 0) "a" else "b"}_turntable")
                .pointerInput(deck.deckIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        inertiaJob?.cancel()
                        inertiaJob = null

                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val radius = (minOf(size.width, size.height) * 0.5f).coerceAtLeast(1f)
                        val dx0 = down.position.x - cx
                        val dy0 = down.position.y - cy
                        val dist = hypot(dx0, dy0)

                        // Dual-Zone Jog Wheel:
                        // Inner 78% platter -> Vinyl Scratching
                        // Outer 22% strobe rim -> Momentary Pitch-Bending
                        val zone = if (dist > radius * 0.78f && latestDeck.isPlaying) {
                            JogTouchZone.PITCH_BEND
                        } else {
                            JogTouchZone.SCRATCH
                        }
                        activeZone = zone

                        var prevAngle = atan2(dy0, dx0)
                        var prevTimeMs = down.uptimeMillis
                        var smoothedDegPerSec = 0f

                        if (zone == JogTouchZone.SCRATCH) {
                            latestOnScratchStart()
                        }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val curTimeMs = change.uptimeMillis
                            val dtMs = (curTimeMs - prevTimeMs).coerceIn(1L, 80L)
                            val curAngle = atan2(change.position.y - cy, change.position.x - cx)
                            var deltaRad = curAngle - prevAngle
                            while (deltaRad > PI) deltaRad -= (2.0 * PI).toFloat()
                            while (deltaRad < -PI) deltaRad += (2.0 * PI).toFloat()
                            val deltaDeg = deltaRad * (180f / PI.toFloat())

                            if (change.position != change.previousPosition) {
                                change.consume()
                            }

                            val instDegPerSec = (deltaDeg / dtMs.toFloat()) * 1000f
                            smoothedDegPerSec = smoothedDegPerSec * 0.55f + instDegPerSec * 0.45f

                            if (zone == JogTouchZone.SCRATCH) {
                                if (abs(deltaDeg) > 0.02f || dtMs >= 32L) {
                                    latestOnScratchDelta(deltaDeg, dtMs)
                                    prevAngle = curAngle
                                    prevTimeMs = curTimeMs
                                }
                            } else {
                                // Map outer strobe-ring angular velocity to momentary pitch bend [-0.08, +0.08]
                                val nominalDegPerSec = if (latestVinylRpm == 45) 270f else 200f
                                val bend = (smoothedDegPerSec / (nominalDegPerSec * 4.5f)).coerceIn(-0.08f, 0.08f)
                                latestOnPitchBend(if (abs(deltaDeg) < 0.05f && dtMs > 45L) 0f else bend)
                                prevAngle = curAngle
                                prevTimeMs = curTimeMs
                            }
                        }

                        // Gesture released cleanly
                        if (zone == JogTouchZone.PITCH_BEND) {
                            latestOnPitchBend(0f)
                            activeZone = JogTouchZone.NONE
                        } else {
                            // Realistic vinyl inertia spin-out when flicked briskly
                            val releaseVelDegPerSec = smoothedDegPerSec
                            if (abs(releaseVelDegPerSec) > 110f) {
                                inertiaJob = scope.launch {
                                    var vel = releaseVelDegPerSec * 0.65f
                                    val stepMs = 16L
                                    while (isActive && abs(vel) > 18f) {
                                        val stepDeg = vel * (stepMs / 1000f)
                                        latestOnScratchDelta(stepDeg, stepMs)
                                        vel *= 0.76f
                                        delay(stepMs)
                                    }
                                    latestOnScratchEnd()
                                    activeZone = JogTouchZone.NONE
                                }
                            } else {
                                latestOnScratchEnd()
                                activeZone = JogTouchZone.NONE
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Static Platter Base + Outer Strobe Bevel + Progress Ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.5f
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val vinylRadius = radius * 0.88f

                // Metallic outer platter bevel (highlights when outer rim pitch-bend is active)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF282D3A), Color(0xFF14171F), Color(0xFF0A0C10)),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )

                if (activeZone == JogTouchZone.PITCH_BEND) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.45f),
                        radius = radius * 0.985f,
                        center = center,
                        style = Stroke(width = 3.5f)
                    )
                }

                // Black vinyl disc
                drawCircle(
                    color = Color(0xFF0B0C0F),
                    radius = vinylRadius,
                    center = center
                )

                // Concentric vinyl grooves
                for (g in 1..10) {
                    val gr = vinylRadius * (0.44f + g * 0.048f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.055f),
                        radius = gr,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }

                // Glowing accent progress arc
                val progress = if (deck.durationMs > 0L) (deck.cursorMs.toFloat() / deck.durationMs).coerceIn(0f, 1f) else 0f
                drawArc(
                    color = accentColor.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - vinylRadius * 0.96f, center.y - vinylRadius * 0.96f),
                    size = Size(vinylRadius * 1.92f, vinylRadius * 1.92f),
                    style = Stroke(width = 3.5f)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - vinylRadius * 0.96f, center.y - vinylRadius * 0.96f),
                    size = Size(vinylRadius * 1.92f, vinylRadius * 1.92f),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )
            }

            // GPU-accelerated rotating strobe dots, specular sheen, and cue needle
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = displayRotation
                    }
            ) {
                val radius = size.minDimension * 0.5f
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                val vinylRadius = radius * 0.88f

                // Strobe dots around outer platter edge
                val dotCount = 36
                for (i in 0 until dotCount) {
                    val a = (i * 2.0 * PI / dotCount).toFloat()
                    val r = radius * 0.942f
                    drawCircle(
                        color = if (deck.isPlaying || activeZone != JogTouchZone.NONE) {
                            accentColor.copy(alpha = 0.78f)
                        } else {
                            Color(0xFF5D687E)
                        },
                        radius = radius * 0.015f,
                        center = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                    )
                }

                // Specular vinyl light sheen
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.12f to Color.White.copy(alpha = 0.08f),
                        0.25f to Color.Transparent,
                        0.62f to Color.White.copy(alpha = 0.06f),
                        0.75f to Color.Transparent
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = true,
                    topLeft = Offset(center.x - vinylRadius, center.y - vinylRadius),
                    size = Size(vinylRadius * 2f, vinylRadius * 2f)
                )

                // Position cue marker line on spinning vinyl
                drawLine(
                    color = accentColor,
                    start = Offset(center.x, center.y - vinylRadius * 0.42f),
                    end = Offset(center.x, center.y - vinylRadius * 0.85f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            }

            // Rotating Center Label with Album Art or Custom Graphic (GPU graphicsLayer rotation)
            Box(
                modifier = Modifier
                    .size(platterSize * 0.37f)
                    .graphicsLayer {
                        rotationZ = displayRotation
                    }
                    .clip(CircleShape)
                    .background(DeckSurface)
                    .border(2.5.dp, accentColor, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val artPath = deck.track?.albumArtPath
                if (artPath != null && File(artPath).exists()) {
                    AsyncImage(
                        model = File(artPath),
                        contentDescription = "Vinyl album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_vinyl_cover_1794566130200),
                        contentDescription = "Vinyl label artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VoidBlack.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Text(
                            text = if (deck.deckIndex == 0) "DECK A" else "DECK B",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = deck.track?.title ?: "EMPTY",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Center Spindle
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD0D7E5))
                        .border(1.5.dp, VoidBlack, CircleShape)
                )
            }

            // Tonearm overlay (pivoted from top-right of platter)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pivot = Offset(size.width * 0.87f, size.height * 0.14f)
                drawCircle(
                    color = Color(0xFF454C5C),
                    radius = size.minDimension * 0.055f,
                    center = pivot
                )
                drawCircle(
                    color = Color(0xFF9AA4B8),
                    radius = size.minDimension * 0.032f,
                    center = pivot
                )

                rotate(degrees = tonearmAngle, pivot = pivot) {
                    val armLen = size.minDimension * 0.46f
                    val elbow = Offset(pivot.x - armLen * 0.08f, pivot.y + armLen * 0.72f)
                    val tip = Offset(pivot.x - armLen * 0.24f, pivot.y + armLen)

                    val armPath = Path().apply {
                        moveTo(pivot.x, pivot.y)
                        lineTo(elbow.x, elbow.y)
                        lineTo(tip.x, tip.y)
                    }
                    drawPath(
                        path = armPath,
                        color = Color(0xFFD7DCE6),
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = accentColor,
                        radius = 5f,
                        center = tip
                    )
                }
            }

            // Compact live HUD pill showing active jog zone (SCRATCH vs BEND)
            if (activeZone != JogTouchZone.NONE || abs(deck.pitchBend) > 0.001f) {
                val badgeText = when {
                    activeZone == JogTouchZone.PITCH_BEND || abs(deck.pitchBend) > 0.001f ->
                        String.format(Locale.US, "BEND %+.1f%%", deck.pitchBend * 100f)
                    else -> "SCRATCH"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoidBlack.copy(alpha = 0.85f))
                        .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
