package com.pawan.hisaab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money

/** Small grey heading that separates sections; optional action on the right. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 28.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = FinTheme.colors.ink2, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = FinTheme.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = FinTheme.colors.hairline)

/** Rounded pill: light fill when idle, solid ink when selected. No borders. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = FinTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) c.ink else c.tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.background else c.ink,
            maxLines = 1,
        )
    }
}

/** Plain text tabs with an underline on the selected one. */
@Composable
fun TextTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = FinTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        options.forEachIndexed { i, label ->
            Column(
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = if (i == selected) c.ink else c.ink3)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(18.dp)
                        .background(if (i == selected) c.ink else Color.Transparent, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/** A tappable list row: leading emoji, title + subtitle, trailing content. */
@Composable
fun ListRow(
    emoji: String?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitleColor: Color = FinTheme.colors.ink2,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (emoji != null) {
            EmojiBadge(emoji)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun EmojiBadge(emoji: String?, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(FinTheme.colors.tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji ?: "?", fontSize = (size.value * 0.42f).sp, color = FinTheme.colors.ink2)
    }
}

/** Amount with tabular digits. Debits in ink, credits in green with a plus. */
@Composable
fun TxnAmount(txn: Txn, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val credit = txn.direction == Direction.CREDIT
    Text(
        (if (credit) "+" else "") + Money.format(txn.amountPaise),
        style = style.merge(Numbers),
        color = if (credit) FinTheme.colors.credit else FinTheme.colors.ink,
        modifier = modifier,
    )
}

@Composable
fun MoneyText(paise: Long, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = FinTheme.colors.ink, signed: Boolean = false) {
    Text(Money.format(paise, signed = signed), style = style.merge(Numbers), color = color, modifier = modifier)
}

/** Borderless text field with an underline, for forms and dialogs. */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    password: Boolean = false,
) {
    val c = FinTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.NumberPassword else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = c.ink,
            unfocusedIndicatorColor = c.hairline,
            focusedLabelColor = c.ink2,
            unfocusedLabelColor = c.ink3,
            cursorColor = c.ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Black primary button, full width. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = FinTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.ink else c.tint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) MaterialTheme.colorScheme.background else c.ink3)
    }
}

/** Quiet secondary action: light fill, ink text. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = FinTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.tint)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) c.ink else c.ink3)
    }
}
