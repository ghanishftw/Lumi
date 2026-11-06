@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.lumi.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import app.lumi.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LumiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun LumiTheme(mode: Int, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(ctx)
        dynamic -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
        }
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), shapes = LumiShapes, content = content)
}

/** Lumi's mark (the same speech bubble as the launcher icon) in a plain circle. */
@Composable
fun Avatar(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(R.drawable.ic_lumi), null, Modifier.size(size * 0.72f), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

/** Rounded pill, tonal when selected. No outlines anywhere. */
@Composable
fun Pill(text: String, icon: ImageVector? = null, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) c.secondaryContainer else c.surfaceContainerHigh, MaterialTheme.motionScheme.fastEffectsSpec(), label = "pill")
    Surface(onClick = onClick, shape = CircleShape, color = bg, modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = if (selected) c.onSecondaryContainer else c.primary); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onSecondaryContainer else c.onSurface)
        }
    }
}

/** A titled, fully-rounded card of settings rows. */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

@Composable
fun Item(
    title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SwitchItem(title: String, subtitle: String?, icon: ImageVector?, checked: Boolean, onChange: (Boolean) -> Unit) =
    Item(title, subtitle, icon, onClick = { onChange(!checked) }) { Switch(checked = checked, onCheckedChange = onChange) }

/** Filled, line-free text field that keeps its own state (no cursor jumps) and reports every change. */
@Composable
fun Field(
    label: String, initial: String, modifier: Modifier = Modifier, secret: Boolean = false, lines: Int = 1,
    onChange: (String) -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    TextField(
        value = v, onValueChange = { v = it; onChange(it) }, label = { Text(label) },
        singleLine = lines == 1, minLines = lines, maxLines = if (lines == 1) 1 else 8,
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) ({ Text(if (show) "Hide" else "Show", Modifier.clip(CircleShape).clickable { show = !show }.padding(8.dp), style = MaterialTheme.typography.labelMedium) }) else null,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Thumb(path: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, modifier.size(size).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.size(size).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lumi", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

fun share(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}
