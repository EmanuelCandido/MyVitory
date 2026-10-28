package com.myvitory.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvitory.app.R
import com.myvitory.app.domain.DayState
import com.myvitory.app.ui.theme.Palette

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/** Botão principal: um por tela, lima, 56–58 dp de altura. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 58.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Lime, contentColor = Palette.Ground),
    ) {
        if (icon != null) {
            AppIcon(icon, null, tint = Palette.Ground, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.1f))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Palette.LineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
    }
}

/** Segmentado em pílula (Esta semana | Plano base). */
@Composable
fun PillTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(Palette.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(if (on) Palette.Text else Color.Transparent)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    color = if (on) Palette.Ground else Palette.TextMuted,
                )
            }
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, background: Color = Palette.SurfaceHigh, content: Color = Palette.Text) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = Palette.TextMuted)
}

/** Marca de status: cada estado tem forma própria, não só cor (✓, ponto, contorno, ✕, traço). */
@Composable
fun StatusMark(state: DayState, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val ring = Modifier.size(size).border(2.dp, Palette.Neutral, CircleShape)
    when (state) {
        DayState.DONE -> Box(modifier.size(size).background(Palette.Lime, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_check, null, tint = Palette.Ground, size = size * 0.6f)
        }
        DayState.PARTIAL -> Box(modifier.size(size).border(2.dp, Palette.Lime, CircleShape).padding(4.dp).clip(CircleShape)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.5f).background(Palette.Lime))
        }
        DayState.MOVED -> Box(modifier.size(size).border(2.dp, Palette.Orange, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_arrow_right, null, tint = Palette.Orange, size = size * 0.55f)
        }
        DayState.UNAVAILABLE -> Box(modifier.size(size).border(2.dp, Palette.Neutral, CircleShape), Alignment.Center) {
            Box(Modifier.width(size * 0.5f).height(2.dp).background(Palette.TextMuted, CircleShape))
        }
        DayState.MISSED, DayState.UNMARKED -> Box(modifier.size(size).border(2.dp, Palette.Orange, CircleShape), Alignment.Center) {
            AppIcon(R.drawable.ic_close, null, tint = Palette.Orange, size = size * 0.5f)
        }
        DayState.TODAY -> Box(modifier.size(size).border(2.dp, Palette.Lime, CircleShape), Alignment.Center) {
            Box(Modifier.size(size * 0.4f).background(Palette.Lime, CircleShape))
        }
        DayState.PLANNED -> Box(modifier.then(ring))
        DayState.REST -> Box(modifier.size(size), Alignment.Center) {
            Box(Modifier.width(size / 2).height(2.dp).background(Palette.Neutral, CircleShape))
        }
    }
}
