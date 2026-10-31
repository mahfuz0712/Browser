package com.mux.android.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun HudField(
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    ime: ImeAction = ImeAction.Done,
    onGo: (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Box(
        modifier
            .background(c.primary.copy(alpha = 0.06f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.5f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) HudText(hint, color = c.dim, size = 13.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(c.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text, imeAction = ime),
            keyboardActions = KeyboardActions(onAny = { onGo?.invoke() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Letter block used when a site has no favicon. */
@Composable
fun Avatar(text: String, modifier: Modifier = Modifier) {
    val c = Hud.colors
    val shape = HudShape(6.dp)
    val letter = text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
    Box(
        modifier
            .background(c.primary.copy(alpha = 0.12f), shape)
            .border(1.dp, c.primary.copy(alpha = 0.55f), shape),
        contentAlignment = Alignment.Center
    ) {
        HudText(letter, color = c.primary, size = 16.sp, bold = true, maxLines = 1)
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HudText(text, color = Hud.colors.dim, size = 12.sp)
    }
}

/** Full-screen HUD page that covers the browser (tabs, bookmarks, history, ...). */
@Composable
fun Overlay(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Hud.colors
    HudBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                HudText("◈ $title", color = c.primary, size = 16.sp, bold = true, modifier = Modifier.weight(1f))
                actions()
                Spacer(Modifier.width(4.dp))
                HudButton("✕", Modifier.width(48.dp).height(38.dp), KeyKind.DANGER, fontSize = 16.sp, onClick = onClose)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun HudDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Hud.colors
    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().background(c.bg, HudShape(12.dp))) {
            HudPanel(Modifier.fillMaxWidth(), title) { content() }
        }
    }
}

@Composable
fun DialogButtons(vararg buttons: Triple<String, KeyKind, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(46.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, kind, action) in buttons) {
            HudButton(label, Modifier.weight(1f).height(46.dp), kind, fontSize = 13.sp, onClick = action)
        }
    }
}
