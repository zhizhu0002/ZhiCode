// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from Miuix LiquidGlassNavigationBar and AndroidLiquidGlass (Apache-2.0).
// Visual/interaction structure follows projects/ksu-style FloatingBottomBar (GPL-3.0),
// but no KSU implementation code is copied into this MIT project.

package com.zhizhu.zhicode.compose.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.zhizhu.zhicode.compose.model.WorkspaceTab
import com.zhizhu.zhicode.compose.theme.ZhiColors
import com.zhizhu.zhicode.compose.ui.effects.inspectDragGestures
import com.zhizhu.zhicode.compose.ui.effects.DampedDragAnimation
import com.zhizhu.zhicode.compose.ui.effects.InnerShadow
import com.zhizhu.zhicode.compose.ui.effects.InteractiveHighlight
import com.zhizhu.zhicode.compose.ui.effects.innerShadow
import com.zhizhu.zhicode.compose.ui.effects.lens
import com.zhizhu.zhicode.compose.ui.effects.rememberCombinedBackdrop
import com.zhizhu.zhicode.compose.ui.effects.vibrancy
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

private val LiquidSpecular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(LightPosition(0.5f, -0.3f, -0.05f), Color.White, 1f),
        secondaryLight = LightSource(LightPosition(0.5f, 0.8f, -0.5f), Color.White, 0.4f),
        dualPeak = true,
    ),
)

private const val LIQUID_LIGHT_REF_X = 0.5f
private const val LIQUID_LIGHT_REF_Y = 0.7f
private const val LIQUID_GRAVITY_THRESHOLD_SQ = 0.01f
private val GRAVITY_STEP = (3.0 * PI / 180.0).toFloat()

/** High-frequency sensor state is quantized and only read while drawing the highlight. */
@Composable
private fun rememberGravityHighlight(base: Highlight, extraDegrees: Float): androidx.compose.runtime.State<Highlight> {
    val tiltState = rememberDeviceTilt()
    val gravityAngle = remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            val gx = tilt.gravityX
            val gy = tilt.gravityY
            if (gx * gx + gy * gy > LIQUID_GRAVITY_THRESHOLD_SQ) {
                (atan2(gy, gx) / GRAVITY_STEP).roundToInt() * GRAVITY_STEP
            } else {
                (-PI / 2).toFloat()
            }
        }
    }
    return remember(base, gravityAngle, extraDegrees) {
        derivedStateOf {
            val style = base.style as BloomStroke
            val primary = style.primaryLight
            val radians = gravityAngle.value + extraDegrees * PI.toFloat() / 180f
            base.copy(
                style = style.copy(
                    primaryLight = primary.copy(
                        position = LightPosition(
                            x = LIQUID_LIGHT_REF_X + cos(radians),
                            y = LIQUID_LIGHT_REF_Y + sin(radians),
                            z = primary.position.z,
                        ),
                    ),
                ),
            )
        }
    }
}

@Composable
internal fun LiquidWorkspaceBar(
    selected: WorkspaceTab,
    onSelect: (WorkspaceTab) -> Unit,
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { WorkspaceTab.entries.toList() }
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOf(selected).coerceIn(0, tabs.lastIndex)
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val onSelectLatest by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isDark = ZhiColors.isDark()
    val scheme = MiuixTheme.colorScheme
    // The caller passes a backdrop captured from the workspace content. This bar is mounted
    // in Scaffold.bottomBar, a sibling of that captured content, so sampling is one-way and
    // cannot capture itself. Honor both the user's blur toggle and platform support.
    val supportedBlur = blurEnabled && backdrop != null && isRuntimeShaderSupported()
    val shape = CircleShape
    val selectedColor = scheme.primary
    val contentColor = scheme.onSurface
    val surface = scheme.surfaceContainer
    val containerColor = surface.copy(alpha = if (supportedBlur) 0.40f else 1f)
    val navigationBottomInset = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
        .asPaddingValues().calculateBottomPadding()
    // KSU's FloatingBottomBar applies exactly one bottom inset in its modifier.
    // This component is itself the bottomBar overlay, so the inset is not also
    // supplied by Scaffold/content padding.
    val bottomPadding = 12.dp + navigationBottomInset
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, tabsBackdrop) else null

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx <= 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx <= 0f) return currentIndex
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - with(density) { 4.dp.toPx() }) / tabWidthPx).toInt().coerceIn(0, tabs.lastIndex)
    }

    val drag = remember(scope, tabs.size, density, isLtr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..tabs.lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { it.x in 0f..totalWidthPx },
            onDragStarted = { pos -> updateValue(indexAt(pos.x).toFloat()) },
            onDragStopped = {
                val target = targetValue.roundToInt().coerceIn(0, tabs.lastIndex)
                if (currentIndex != target) {
                    currentIndex = target
                    onSelectLatest(tabs[target])
                }
                updateValue(target.toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                scope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, amount ->
                if (tabWidthPx > 0f && amount.x != 0f) {
                    updateValue((targetValue + amount.x / tabWidthPx * if (isLtr) 1f else -1f).coerceIn(0f, tabs.lastIndex.toFloat()))
                    scope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                }
            },
        )
    }

    LaunchedEffect(selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            drag.animateToValue(selectedIndex.toFloat())
        }
    }

    fun activate(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onSelectLatest(tabs[index])
        }
        drag.animateToValue(index.toFloat())
    }

    val interactive = remember(scope, isLtr, drag) {
        InteractiveHighlight(scope) { size, _ ->
            Offset(
                x = if (isLtr) (drag.value + 0.5f) * tabWidthPx + panelOffset else size.width - (drag.value + 0.5f) * tabWidthPx + panelOffset,
                y = size.height / 2f,
            )
        }
    }
    val baseHighlight = rememberGravityHighlight(LiquidSpecular, -45f)
    val pillHighlight = rememberGravityHighlight(LiquidSpecular, 90f)
    val tabContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        val scale = LocalLiquidTabScale.current
        tabs.forEachIndexed { index, tab ->
            Column(
                modifier = Modifier
                    .semantics(mergeDescendants = true) {
                        this.selected = index == currentIndex
                        role = Role.Tab
                        onClick { activate(index); true }
                    }
                    .onKeyEvent { event ->
                        val activation = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                        if (activation) { if (event.type == KeyEventType.KeyUp) activate(index); true } else false
                    }
                    .focusable()
                    .defaultMinSize(minWidth = 76.dp)
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = scale(); scaleY = scale() },
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(imageVector = workspaceTabIcon(tab), contentDescription = null, modifier = Modifier.size(22.dp))
                // 字阶走 Miuix 的 `footnote2`（11sp）—— 与原来写死的 11sp 同号，
                // 但从此跟随主题的字号表，而不是自己维护第二个数字。
                Text(
                    text = tab.label,
                    style = MiuixTheme.textStyles.footnote2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Box(
        // 宽度与输入器**齐平**：`FloatingBottomShell` 给输入器的横向内缩是
        // `floatingHorizontalInset(wide)`（窄屏 12dp），这里用同一个来源。
        // 以前是 `width(IntrinsicSize.Min)`（按标签内容收窄并居中），于是胶囊
        // 比上面的输入器短一截，两块的左右边缘对不上 —— 看起来像没对齐。
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = floatingHorizontalInset(wide = false))
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Row(
                    modifier = Modifier
                        .selectableGroup()
                        .onSizeChanged { size ->
                            totalWidthPx = size.width.toFloat()
                            tabWidthPx = (totalWidthPx - with(density) { 8.dp.toPx() }) / tabs.size
                        }
                        .graphicsLayer { translationX = panelOffset }
                        .dropShadow(shape, Shadow(radius = 10.dp, color = Color.Black, alpha = if (isDark) 0.20f else 0.10f))
                        .then(
                            if (supportedBlur) Modifier.drawBackdrop(
                                backdrop = backdrop!!,
                                shape = { shape },
                                effects = {
                                    padding = maxOf(padding, 40.dp.toPx())
                                    vibrancy()
                                    blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
                                },
                                highlight = { baseHighlight.value.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val s = lerp(1f, 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f), drag.pressProgress)
                                    scaleX = s; scaleY = s
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            ) else Modifier.background(surface, shape)
                        )
                        .then(if (supportedBlur) interactive.modifier.then(interactive.gestureModifier) else Modifier)
                        .then(drag.modifier)
                        .height(FloatingNavBarHeight)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabContent,
                )
            }

            // Hidden duplicate is intentionally drawn into a second backdrop: this is the source
            // used by the moving lens indicator, matching the official Miuix/KSU liquid-glass stack.
            if (supportedBlur && backdrop != null) {
                CompositionLocalProvider(
                    LocalLiquidTabScale provides { lerp(1f, 1.2f, drag.pressProgress) },
                    LocalContentColor provides selectedColor,
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { shape },
                                effects = {
                                    padding = maxOf(padding, 40.dp.toPx())
                                    vibrancy(); blur(4.dp.toPx(), 4.dp.toPx())
                                    lens(refractionHeight = 24.dp.toPx(), refractionAmount = 24.dp.toPx())
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                            .then(interactive.modifier)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabContent,
                    )
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidth = with(density) { tabWidthPx.toDp() }
                val progressOffset = drag.value * tabWidthPx
                if (supportedBlur && combinedBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer { translationX = (if (isLtr) progressOffset else -progressOffset) + panelOffset }
                            .drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { shape },
                                effects = {
                                    val p = drag.pressProgress
                                    lens(refractionHeight = 10.dp.toPx() * p, refractionAmount = 14.dp.toPx() * p, depthEffect = true, chromaticAberration = 0.5f)
                                },
                                highlight = { pillHighlight.value.copy(alpha = drag.pressProgress) },
                                layerBlock = {
                                    scaleX = drag.scaleX; scaleY = drag.scaleY
                                    val velocity = drag.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val p = drag.pressProgress
                                    drawRect(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f), alpha = 1f - p)
                                    drawRect(Color.Black.copy(alpha = 0.03f * p))
                                },
                            )
                            .innerShadow(shape) {
                                InnerShadow(radius = 8.dp * drag.pressProgress, color = Color.Black.copy(alpha = 0.15f), alpha = drag.pressProgress)
                            }
                            .height(56.dp)
                            .width(tabWidth),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer { translationX = (if (isLtr) progressOffset else -progressOffset) + panelOffset }
                            .clip(shape)
                            .background(selectedColor.copy(alpha = 0.15f), shape)
                            .height(56.dp)
                            .width(tabWidth),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides selectedColor) {
                            Row(
                                modifier = Modifier
                                    .clearAndSetSemantics {}
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                    .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                    .height(56.dp)
                                    .graphicsLayer { translationX = if (isLtr) -progressOffset else progressOffset },
                                verticalAlignment = Alignment.CenterVertically,
                                content = tabContent,
                            )
                        }
                    }
                }
            }
        }
    }

private fun workspaceTabIcon(tab: WorkspaceTab): androidx.compose.ui.graphics.vector.ImageVector = when (tab) {
    WorkspaceTab.CHAT -> ZhiMaterialIcons.ChatBubble
    WorkspaceTab.TERMINAL -> ZhiMaterialIcons.Terminal
    WorkspaceTab.FILES -> ZhiMaterialIcons.Folder
}
