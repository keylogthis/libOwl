package org.thebottle.libOwl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.thebottle.libOwl.data.CoverStore
import kotlin.math.ceil

private val StarGold = Color(0xFFFFB300)

/**
 * 5-star bar with half-star support. [rating] is in half-star units (0..10, so 7 = 3.5 stars).
 * Read-only when [onChange] is null. Tap the left half of a star for a half, the right half for a
 * whole star; drag across the bar to adjust; tapping the current value again clears the rating.
 */
@Composable
fun RatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp,
    onChange: ((Int) -> Unit)? = null
) {
    fun halfAt(x: Float, width: Int): Int =
        ceil(x / width * 10f).toInt().coerceIn(1, 10)

    val base = modifier.semantics {
        contentDescription = "Rating ${(rating / 2f).toString().removeSuffix(".0")} out of 5 stars"
    }
    val rowMod = if (onChange != null) {
        base
            .pointerInput(rating) {
                detectTapGestures { o ->
                    val v = halfAt(o.x, size.width)
                    onChange(if (v == rating) 0 else v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onChange(halfAt(change.position.x, size.width))
                }
            }
    } else base

    Row(rowMod) {
        for (i in 1..5) {
            // 2 = full, 1 = half, 0 = empty
            val fill = when {
                rating >= i * 2 -> 2
                rating == i * 2 - 1 -> 1
                else -> 0
            }
            Star(fill, starSize)
        }
    }
}

@Composable
private fun Star(fill: Int, size: Dp) {
    val empty = MaterialTheme.colorScheme.onSurfaceVariant
    when (fill) {
        2 -> Icon(Icons.Filled.Star, null, tint = StarGold, modifier = Modifier.size(size))
        0 -> Icon(Icons.Filled.StarBorder, null, tint = empty, modifier = Modifier.size(size))
        else -> Box(Modifier.size(size)) {
            Icon(Icons.Filled.StarBorder, null, tint = empty, modifier = Modifier.size(size))
            Icon(
                Icons.Filled.Star, null, tint = StarGold,
                modifier = Modifier
                    .size(size)
                    .drawWithContent {
                        clipRect(right = this.size.width / 2f) { this@drawWithContent.drawContent() }
                    }
            )
        }
    }
}

@Composable
fun CoverImage(covers: CoverStore, file: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (file != null) {
            AsyncImage(
                model = covers.file(file),
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.Book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-screen cover viewer with pinch-to-zoom and pan. Back button or the X closes it. */
@Composable
fun CoverViewerDialog(covers: CoverStore, file: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = covers.file(file),
                contentDescription = "Cover",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
        }
    }
}
