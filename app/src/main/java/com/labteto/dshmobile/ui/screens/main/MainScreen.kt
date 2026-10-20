package com.labteto.dshmobile.ui.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labteto.dshmobile.R
import com.labteto.dshmobile.ui.components.DsIconButton
import com.labteto.dshmobile.ui.components.DsToastHost
import com.labteto.dshmobile.ui.components.rememberDsToast
import com.labteto.dshmobile.ui.rememberSessionStore
import com.labteto.dshmobile.ui.theme.DsAnimations
import com.labteto.dshmobile.ui.theme.DsSpacing
import com.labteto.dshmobile.ui.theme.DsTheme
import kotlinx.coroutines.launch

/** Width at which the foldable/tablet shell takes over from the phone shell. */
private const val EXPANDED_WIDTH_DP = 600

/** Persistent session list on the wide shell. */
private const val SIDEBAR_WIDTH_DP = 300

/** Icon-only rail, used when the window is wide but not tablet-wide. */
private const val RAIL_WIDTH_DP = 56

/**
 * Above this width the session list opens by default; below it the rail does. The rail keeps the
 * transcript useful on a landscape outer screen or a portrait inner screen, where a 300dp list
 * would eat half the window.
 */
private const val SIDEBAR_EXPAND_BREAKPOINT_DP = 720

/** Session details column on the wide shell. */
private const val DETAILS_WIDTH_DP = 340

private const val SIDEBAR_EXPANDED = 0
private const val SIDEBAR_RAIL = 1
private const val SIDEBAR_HIDDEN = 2

/**
 * Two shells, chosen by width:
 *
 * - **Compact** (phones, the outer screen of a foldable): the original Discord-style drawer.
 * - **Wide** (tablet-sized inner screen, or a vertical hinge): the session list is a permanent
 *   left column, the chat gets the remaining width, and the details panel is a third column
 *   instead of an overlay.
 *
 * The fold state is observed rather than guessed so a vertical hinge also selects the wide shell
 * even if the window reports a width just under the breakpoint.
 */
@Composable
fun MainScreen(onOpenSettings: () -> Unit) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val fold = rememberFoldState()
    val wide = widthDp >= EXPANDED_WIDTH_DP || fold.verticalHinge

    var automationsOpen by rememberSaveable { mutableStateOf(false) }
    var automationSession by rememberSaveable { mutableStateOf<String?>(null) }
    val store = rememberSessionStore()
    val automationAvailable by store.automationAvailable.collectAsStateWithLifecycle()
    val toast = rememberDsToast()
    val unsupportedAutomation = stringResource(R.string.ux_b_automation_unavailable)

    LaunchedEffect(automationAvailable, automationsOpen) {
        if (automationAvailable == false && automationsOpen) {
            automationsOpen = false
            automationSession = null
            toast.second(unsupportedAutomation)
        }
    }

    val openAutomations = {
        automationSession = store.currentSessionId.value
        automationsOpen = true
    }

    if (wide) {
        WideMainScreen(
            onOpenSettings = onOpenSettings,
            onOpenAutomations = openAutomations,
            toast = toast,
        )
    } else {
        CompactMainScreen(
            onOpenSettings = onOpenSettings,
            onOpenAutomations = openAutomations,
            toast = toast,
        )
    }

    if (automationsOpen && automationAvailable != false) {
        AutomationScreen(automationSession, onClose = { automationsOpen = false })
    }
}

/**
 * The original phone shell: the chat list is a drawer, and details slide in from the right edge.
 *
 * The details edge gesture only claims the drags it owns (leftward from the right edge band, or
 * any drag on the details area while it is open) and leaves every other horizontal drag
 * unconsumed for the drawer's built-in open gesture.
 */
@Composable
private fun CompactMainScreen(
    onOpenSettings: () -> Unit,
    onOpenAutomations: () -> Unit,
    toast: Pair<State<String?>, (String) -> Unit>,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val detailsWidth = 300.dp

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatListDrawer(
                onClose = { scope.launch { drawerState.close() } },
                onOpenSettings = onOpenSettings,
                onOpenAutomations = {
                    onOpenAutomations()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detailsOpen) {
                    val width = size.width.toFloat()
                    val edgeBandPx = 28.dp.toPx()
                    val detailsAreaPx = detailsWidth.toPx() * 0.9f
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val owned = if (!detailsOpen) {
                            startX >= width - edgeBandPx
                        } else {
                            startX <= detailsAreaPx
                        }
                        if (!owned) return@awaitEachGesture

                        var claimed = false
                        awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            claimed = detailsOpen || overSlop < 0f
                            if (claimed) change.consume()
                        } ?: return@awaitEachGesture
                        if (!claimed) return@awaitEachGesture

                        var totalX = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            if (change.isConsumed) break
                            totalX += change.positionChange().x
                            change.consume()
                            val threshold = width * 0.12f
                            if (!detailsOpen && totalX <= -threshold) {
                                detailsOpen = true
                                break
                            }
                            if (detailsOpen && totalX >= threshold) {
                                detailsOpen = false
                                break
                            }
                        }
                    }
                },
        ) {
            val blur by animateDpAsState(
                if (detailsOpen) 6.dp else 0.dp,
                tween(220, easing = FastOutSlowInEasing),
                label = "detailsBlur",
            )
            Box(Modifier.fillMaxSize().blur(blur)) {
                ChatScreen(
                    onOpenDetails = { detailsOpen = true },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    detailsOpen = detailsOpen,
                )
            }

            AnimatedVisibility(
                visible = detailsOpen,
                enter = fadeIn(DsAnimations.fade),
                exit = fadeOut(DsAnimations.fade),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(DsTheme.colors.overlayMask)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClickLabel = stringResource(R.string.common_close),
                        ) { detailsOpen = false },
                )
            }

            AnimatedVisibility(
                visible = detailsOpen,
                enter = slideInHorizontally(DsAnimations.panelSlide) { it },
                exit = slideOutHorizontally(DsAnimations.panelSlide) { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                DetailsPanel(
                    onClose = { detailsOpen = false },
                    onOpenAutomations = {
                        onOpenAutomations()
                        detailsOpen = false
                    },
                    modifier = Modifier.width(detailsWidth),
                )
            }
            DsToastHost(toast, Modifier.align(Alignment.TopCenter))
        }
    }
}

/**
 * The wide shell: three columns, all persistent.
 *
 * The chat keeps the flexible middle so the transcript grows with the window; the two rails stay
 * at fixed widths because their content is a list and a form, neither of which benefits from
 * stretching. Details can still be closed, which hands the whole middle to the chat.
 */
@Composable
private fun WideMainScreen(
    onOpenSettings: () -> Unit,
    onOpenAutomations: () -> Unit,
    toast: Pair<State<String?>, (String) -> Unit>,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    var sidebar by rememberSaveable {
        mutableIntStateOf(
            if (widthDp >= SIDEBAR_EXPAND_BREAKPOINT_DP) SIDEBAR_EXPANDED else SIDEBAR_RAIL,
        )
    }
    // Once the user picks a sidebar mode, folding the phone should not silently undo the choice.
    var sidebarChosen by rememberSaveable { mutableStateOf(false) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(widthDp) {
        if (!sidebarChosen) {
            sidebar = if (widthDp >= SIDEBAR_EXPAND_BREAKPOINT_DP) SIDEBAR_EXPANDED else SIDEBAR_RAIL
        }
    }

    fun chooseSidebar(mode: Int) {
        sidebar = mode
        sidebarChosen = true
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            when (sidebar) {
                SIDEBAR_EXPANDED -> {
                    ChatListDrawer(
                        onClose = {},
                        onOpenSettings = onOpenSettings,
                        onOpenAutomations = onOpenAutomations,
                        modifier = Modifier.width(SIDEBAR_WIDTH_DP.dp).fillMaxHeight(),
                        onCollapse = { chooseSidebar(SIDEBAR_RAIL) },
                    )
                    VerticalDivider(color = DsTheme.colors.borderL1)
                }

                SIDEBAR_RAIL -> {
                    SessionRail(
                        onExpand = { chooseSidebar(SIDEBAR_EXPANDED) },
                        onOpenSettings = onOpenSettings,
                        onHide = { chooseSidebar(SIDEBAR_HIDDEN) },
                    )
                    VerticalDivider(color = DsTheme.colors.borderL1)
                }
            }

            ChatScreen(
                onOpenDetails = { detailsOpen = !detailsOpen },
                onOpenDrawer = {
                    chooseSidebar(
                        if (widthDp >= SIDEBAR_EXPAND_BREAKPOINT_DP) {
                            SIDEBAR_EXPANDED
                        } else {
                            SIDEBAR_RAIL
                        },
                    )
                },
                detailsOpen = detailsOpen,
                showDrawerButton = sidebar == SIDEBAR_HIDDEN,
                modifier = Modifier.weight(1f),
            )
            if (detailsOpen) {
                VerticalDivider(color = DsTheme.colors.borderL1)
                DetailsPanel(
                    onClose = { detailsOpen = false },
                    onOpenAutomations = {
                        onOpenAutomations()
                        detailsOpen = false
                    },
                    modifier = Modifier.width(DETAILS_WIDTH_DP.dp),
                )
            }
        }
        DsToastHost(toast, Modifier.align(Alignment.TopCenter))
    }
}

/**
 * The narrow form of the sidebar: expand, settings, and hide. It is deliberately icon-only —
 * the whole point is to leave the transcript the width the phone has.
 */
@Composable
private fun SessionRail(
    onExpand: () -> Unit,
    onOpenSettings: () -> Unit,
    onHide: () -> Unit,
) {
    val colors = DsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH_DP.dp)
            .background(colors.sidebar)
            .safeDrawingPadding()
            .padding(vertical = DsSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DsSpacing.small),
    ) {
        DsIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.chatlist_open),
            onClick = onExpand,
            tint = colors.labelSecondary,
            iconSize = 18.dp,
        )
        DsIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = stringResource(R.string.settings_title),
            onClick = onOpenSettings,
            tint = colors.labelTertiary,
            iconSize = 18.dp,
        )
        Spacer(Modifier.weight(1f))
        DsIconButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.common_close),
            onClick = onHide,
            tint = colors.labelTertiary,
            iconSize = 18.dp,
        )
    }
}
