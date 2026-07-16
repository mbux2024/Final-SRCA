package com.streambert.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A D-pad-friendly single-line text field. Built on [BasicTextField] so it
 * works under the TV Material theme without pulling in mobile Material3.
 * Focusing it (via OK on the remote) opens the system IME.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Done,
    singleLine: Boolean = true,
    onImeAction: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    androidx.compose.foundation.layout.Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else colors.border,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                cursorBrush = SolidColor(colors.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onAny = { onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
