package com.labteto.dshmobile.ui.screens.main

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.collect

/**
 * The state of the foldable hinge, if this device has one.
 *
 * A book-style foldable reports a [FoldingFeature] while it is partially folded. When it is
 * fully open or fully closed there is no separating feature, and [separating] is false.
 */
data class FoldState(
    val separating: Boolean = false,
    val orientation: FoldingFeature.Orientation? = null,
    val hingeBounds: Rect? = null,
) {
    val verticalHinge: Boolean
        get() = separating && orientation == FoldingFeature.Orientation.VERTICAL

    val horizontalHinge: Boolean
        get() = separating && orientation == FoldingFeature.Orientation.HORIZONTAL
}

@Composable
fun rememberFoldState(): FoldState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var state by remember { mutableStateOf(FoldState()) }

    LaunchedEffect(activity) {
        val host = activity ?: return@LaunchedEffect
        WindowInfoTracker.getOrCreate(host)
            .windowLayoutInfo(host)
            .collect { layoutInfo ->
                val feature = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
                state = if (feature == null) {
                    FoldState()
                } else {
                    FoldState(
                        separating = feature.isSeparating,
                        orientation = feature.orientation,
                        hingeBounds = feature.bounds,
                    )
                }
            }
    }

    return state
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
