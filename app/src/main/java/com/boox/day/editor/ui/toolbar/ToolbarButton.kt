package com.boox.day.editor.ui.toolbar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import com.boox.day.R
import com.boox.day.ui.theme.EInkTheme
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Size scale for toolbar buttons and their icons. The compact toolbar's "…" popup provides a larger
 * scale so the tools in it are easier to see and tap (Oct 2026); everywhere else it is 1.
 */
val LocalToolbarIconScale = staticCompositionLocalOf { 1f }

@Composable
fun ToolbarButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    onSelect: () -> Unit = {},
    iconId: Int? = null,
    vectorIcon: ImageVector? = null,
    imageVector: ImageVector? = null,
    text: String? = null,
    penColor: Color? = null,
    hasDropdown: Boolean = false,
    contentDescription: String = "",
    iconPadding: Int = 8,  // Default padding (8dp), increase to make icon smaller
    iconSize: Dp = EInkTheme.Toolbar.iconSize,  // Default icon size, override for mini toolbars
    pocketBar: Boolean = false  // On the pocket (Palma) bar: one button and icon size, a bar under the selected tool
) {
    // Track press state for visual feedback (E-Ink friendly: instant color inversion, no ripple)
    val interactionSource = remember { MutableInteractionSource() }
    val scale = LocalToolbarIconScale.current
    val isPressed by interactionSource.collectIsPressedAsState()

    // Pocket bar (Palma, Nov 2026): the selected tool has a bar under it instead of the inverted
    // fill; a press still inverts
    val selectedBar = pocketBar && isSelected && !isPressed && enabled
    val inverted = isPressed || (isSelected && !selectedBar)

    // Disabled: no selection, grayed out icon
    // Unselected: transparent background, icon tinted with penColor (or black)
    // Pressed/Selected: filled background with penColor (or black), white icon (inverted selection)
    val bgColor = when {
        !enabled -> EInkTheme.Toolbar.buttonBackground
        inverted -> (penColor ?: EInkTheme.Selection.background)
        else -> EInkTheme.Toolbar.buttonBackground
    }
    val tint = when {
        !enabled -> EInkTheme.Toolbar.iconColorDisabled
        inverted -> EInkTheme.Selection.foreground
        penColor != null -> penColor
        else -> EInkTheme.Toolbar.iconColor
    }
    val barColor = penColor ?: EInkTheme.Selection.background

    // If this button is configured with a special placeholder text and no icon,
    // use the standard undo icon so mini toolbars can share the same visual
    // language as the main drawing toolbar without changing call sites.
    val effectiveIconId = when {
        iconId != null -> iconId
        text == "?" -> R.drawable.undo_40px
        else -> null
    }

    Box(
        (if (pocketBar) Modifier.size(POCKET_BUTTON_SIZE) else Modifier)
            .then(modifier)
            .then(Modifier.size(EInkTheme.Toolbar.buttonSize * scale))  // Default size, but passed modifier's size takes precedence
            .alpha(if (enabled) 1f else 0.35f)  // More obvious disabled state
            .clickable(
                enabled = enabled,
                indication = null,  // No ripple - E-Ink friendly
                interactionSource = interactionSource
            ) { onSelect() }
            .background(
                color = bgColor, shape = RoundedCornerShape(EInkTheme.ModernDialog.PrimaryButton.cornerRadius)
            )
            .drawBehind {
                if (selectedBar) {
                    val barWidth = SELECTED_BAR_WIDTH.toPx()
                    val barHeight = SELECTED_BAR_HEIGHT.toPx()
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset((size.width - barWidth) / 2, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2)
                    )
                }
            }
            .padding(if (pocketBar) POCKET_ICON_PADDING else iconPadding.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            effectiveIconId != null -> {
                Icon(
                    painterResource(effectiveIconId),
                    contentDescription,
                    modifier = if (pocketBar) Modifier.fillMaxSize() else Modifier.size(iconSize * scale),
                    tint = tint
                )
            }

            vectorIcon != null -> {
                Icon(
                    vectorIcon,
                    contentDescription,
                    modifier = if (pocketBar) Modifier.fillMaxSize() else Modifier.size(iconSize * scale),
                    tint = tint
                )
            }

            imageVector != null -> {
                Icon(
                    imageVector,
                    contentDescription,
                    modifier = if (pocketBar) Modifier.fillMaxSize() else Modifier.size(iconSize * scale),
                    tint = if (inverted) EInkTheme.Selection.foreground else EInkTheme.Toolbar.iconColor
                )
            }
            text != null -> {
                Text(
                    text,
                    fontSize = 20.sp,
                    color = if (inverted) EInkTheme.Selection.foreground else EInkTheme.Toolbar.iconColor
                )
            }
        }

        // Dropdown indicator triangle in bottom-right corner; not on the pocket bar (tap the selected tool again)
        if (hasDropdown && !pocketBar) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 4.dp)
                    .size(6.dp)
            ) {
                val triangleColor = if (inverted) EInkTheme.Selection.foreground else EInkTheme.Colors.darkGray
                val path = Path().apply {
                    moveTo(size.width, 0f)           // top-right
                    lineTo(size.width, size.height)  // bottom-right
                    lineTo(0f, size.height)          // bottom-left
                    close()
                }
                drawPath(path, triangleColor)
            }
        }
    }
}

private val POCKET_BUTTON_SIZE = 44.dp
private val POCKET_ICON_PADDING = 9.dp
private val SELECTED_BAR_WIDTH = 24.dp
private val SELECTED_BAR_HEIGHT = 3.dp
