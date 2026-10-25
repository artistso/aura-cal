package com.example.aura.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

enum class AuraIconName {
    Undo, Redo, Plus, Pen, Export, Fit, Trash, Collapse, Expand, Layout,
    Pointer, Marquee, Close, Copy, Check, Chevron, Spark, Warning, Error,
    Docs, Port, Bind, Graph, History, Menu, Solve, Cube3D
}

@Composable
fun AuraIcon(
    name: AuraIconName,
    modifier: Modifier = Modifier.size(16.dp),
    tint: Color = Color.Unspecified
) {
    val vector: ImageVector = when (name) {
        AuraIconName.Undo -> Icons.AutoMirrored.Outlined.Undo
        AuraIconName.Redo -> Icons.AutoMirrored.Outlined.Redo
        AuraIconName.Plus -> Icons.Outlined.Add
        AuraIconName.Pen -> Icons.Outlined.Edit
        AuraIconName.Export -> Icons.Outlined.IosShare
        AuraIconName.Fit -> Icons.Outlined.CenterFocusStrong
        AuraIconName.Trash -> Icons.Outlined.DeleteOutline
        AuraIconName.Collapse -> Icons.Outlined.CloseFullscreen
        AuraIconName.Expand -> Icons.Outlined.OpenInFull
        AuraIconName.Layout -> Icons.Outlined.AccountTree
        AuraIconName.Pointer -> Icons.Outlined.PanToolAlt
        AuraIconName.Marquee -> Icons.Outlined.SelectAll
        AuraIconName.Close -> Icons.Outlined.Close
        AuraIconName.Copy -> Icons.Outlined.ContentCopy
        AuraIconName.Check -> Icons.Outlined.Check
        AuraIconName.Chevron -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
        AuraIconName.Spark -> Icons.Outlined.AutoAwesome
        AuraIconName.Warning -> Icons.Outlined.WarningAmber
        AuraIconName.Error -> Icons.Outlined.ErrorOutline
        AuraIconName.Docs -> Icons.Outlined.Description
        AuraIconName.Port -> Icons.Outlined.Adjust
        AuraIconName.Bind -> Icons.Outlined.Link
        AuraIconName.Graph -> Icons.Outlined.Hub
        AuraIconName.History -> Icons.Outlined.History
        AuraIconName.Menu -> Icons.Outlined.Menu
        AuraIconName.Solve -> Icons.Outlined.Functions
        AuraIconName.Cube3D -> Icons.Outlined.ViewInAr
    }
    Icon(
        imageVector = vector,
        contentDescription = name.name,
        modifier = modifier,
        tint = if (tint == Color.Unspecified) AuraDim else tint
    )
}

@Composable
fun AuraPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = AuraLine,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(AuraPanelTop, AuraPanelBottom)
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        content = content
    )
}

@Composable
fun EyebrowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AuraMuted
) {
    Text(
        text = text.uppercase(),
        fontFamily = AuraSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
fun AuraHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AuraLine)
    )
}

@Composable
fun AuraPill(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = AuraDim,
    borderColor: Color = AuraLine,
    backgroundColor: Color = Color(0x0D94A3B8),
    dotColor: Color? = null,
    leadingIcon: AuraIconName? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = CircleShape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        if (leadingIcon != null) {
            AuraIcon(name = leadingIcon, modifier = Modifier.size(11.dp), tint = textColor)
        }
        Text(
            text = text,
            fontFamily = AuraMonoFontFamily,
            fontSize = 10.5.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class AuraBtnVariant {
    Default, Primary, Active, Danger
}

@Composable
fun AuraBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AuraBtnVariant = AuraBtnVariant.Default,
    enabled: Boolean = true,
    height: Dp = 36.dp,
    horizontalPadding: Dp = 11.dp,
    fontSize: TextUnit = 12.sp,
    testTag: String? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(11.dp)
    val bgBrush = when (variant) {
        AuraBtnVariant.Primary -> Brush.verticalGradient(
            listOf(AuraAmberBright, Color(0xFFE2A144))
        )
        AuraBtnVariant.Active -> Brush.verticalGradient(
            listOf(Color(0x26F0B45A), Color(0x1AF0B45A))
        )
        AuraBtnVariant.Danger -> Brush.verticalGradient(
            listOf(Color(0x1AFB7185), Color(0x12FB7185))
        )
        AuraBtnVariant.Default -> Brush.verticalGradient(
            listOf(Color(0x1494A3B8), Color(0x0D94A3B8))
        )
    }
    val borderColor = when (variant) {
        AuraBtnVariant.Primary -> Color(0xA6FFDCA0)
        AuraBtnVariant.Active -> Color(0x73F0B45A)
        AuraBtnVariant.Danger -> Color(0x66FB7185)
        AuraBtnVariant.Default -> AuraLine
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .height(height)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(bgBrush)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        content = content
    )
}
