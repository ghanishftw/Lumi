@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.lumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface Block
private data class Para(val text: String) : Block
private data class Heading(val level: Int, val text: String) : Block
private data class Code(val lang: String, val code: String) : Block
private data class Bullet(val indent: Int, val marker: String, val text: String) : Block
private data class Quote(val text: String) : Block
private data class Table(val rows: List<List<String>>) : Block
private data object Gap : Block

private val LIST = Regex("^([-*+]|\\d+[.)])\\s+(.*)")
private val RULE = Regex("^([-*_])(\\s*\\1){2,}\\s*$")
private val TABLE_SEP = Regex("^\\|?\\s*:?-{2,}.*")
private val TABLE_SEP_ROW = Regex("^\\|?[\\s:|-]+\\|?$")

private fun parse(md: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = md.lines()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += Para(para.toString().trim()); para.clear() }
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trimStart()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.appendLine(lines[i]); i++ }
                out += Code(lang, code.toString().trimEnd('\n'))
            }
            t.startsWith("#") && ' ' in t && t.substringBefore(' ').all { it == '#' } -> {
                flush(); out += Heading(t.substringBefore(' ').length, t.substringAfter(' ').trim().trimEnd('#').trim())
            }
            RULE.matches(t) -> { flush(); out += Gap }
            LIST.find(t) != null -> {
                flush()
                val m = LIST.find(t)!!
                out += Bullet((raw.length - t.length) / 2, m.groupValues[1], m.groupValues[2])
            }
            t.startsWith(">") -> {
                flush()
                val q = StringBuilder()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) { q.appendLine(lines[i].trimStart().removePrefix(">").trim()); i++ }
                out += Quote(q.toString().trim())
                continue
            }
            t.startsWith("|") && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1].trim()) -> {
                flush()
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val r = lines[i].trim()
                    if (!TABLE_SEP_ROW.matches(r)) rows += r.trim('|').split("|").map { it.trim() }
                    i++
                }
                out += Table(rows)
                continue
            }
            t.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(t) }
        }
        i++
    }
    flush()
    return out
}

private val INLINE = Regex(
    """(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(__[^_\n]+__)|(~~[^~\n]+~~)|(\*[^*\s][^*\n]*\*)|((?<![\w])_[^_\s][^_\n]*_(?![\w]))|(\[([^\]\n]+)]\(([^)\s]+)\))|(https?://[^\s)\]]+)"""
)

fun inline(s: String, c: ColorScheme): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(s)) {
        append(s.substring(last, m.range.first))
        val v = m.value
        when {
            v.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.tertiary, fontWeight = FontWeight.Medium)) { append(v.trim('`')) }
            v.startsWith("**") || v.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(v.substring(2, v.length - 2), c)) }
            v.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(v.substring(2, v.length - 2)) }
            v.startsWith("*") || v.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.substring(1, v.length - 1)) }
            v.startsWith("[") -> withLink(LinkAnnotation.Url(m.groupValues[9], TextLinkStyles(SpanStyle(color = c.primary, fontWeight = FontWeight.Medium)))) { append(m.groupValues[8]) }
            else -> withLink(LinkAnnotation.Url(v, TextLinkStyles(SpanStyle(color = c.primary)))) { append(v) }
        }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

@Composable
fun Markdown(md: String, modifier: Modifier = Modifier) {
    val blocks = remember(md) { parse(md) }
    val c = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Para -> Text(inline(b.text, c), style = ty.bodyLarge)
                is Heading -> Text(
                    inline(b.text, c), Modifier.padding(top = 6.dp),
                    style = when (b.level) { 1 -> ty.headlineSmallEmphasized; 2 -> ty.titleLargeEmphasized; else -> ty.titleMediumEmphasized },
                )
                is Code -> CodeBlock(b.lang, b.code)
                is Bullet -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                    Text(
                        if (b.marker[0].isDigit()) b.marker else "•", Modifier.widthIn(min = 22.dp).padding(end = 6.dp),
                        style = ty.bodyLarge, color = c.primary, fontWeight = FontWeight.SemiBold,
                    )
                    Text(inline(b.text, c), style = ty.bodyLarge)
                }
                is Quote -> Surface(shape = RoundedCornerShape(24.dp), color = c.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Text(inline(b.text, c), Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = ty.bodyLarge, fontStyle = FontStyle.Italic, color = c.onSurfaceVariant)
                }
                is Table -> TableBlock(b.rows)
                Gap -> Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Code with no borders, no line numbers, no dividers: a soft tonal card, a label and a copy button. */
@Composable
private fun CodeBlock(lang: String, code: String) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val styled = remember(code, c) { highlight(code, c) }
    Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lang.ifBlank { "code" }, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { copy(ctx, code) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.ContentCopy, "Copy code", Modifier.size(18.dp)) }
            }
            Text(
                styled, softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp, color = c.onSurface),
            )
        }
    }
}

private val KEYWORDS = Regex(
    "\\b(fun|val|var|class|object|interface|data|sealed|enum|struct|impl|trait|fn|let|const|mut|def|function|func|return|if|else|elif|when|match|switch|case|for|while|do|break|continue|" +
        "import|from|package|use|export|default|public|private|protected|internal|static|final|override|abstract|suspend|async|await|yield|lambda|" +
        "new|try|catch|except|finally|throw|throws|raise|with|as|is|in|not|and|or|true|false|null|nil|None|True|False|self|this|super|void|int|string|bool|type|where|select)\\b"
)
private val TOKENS = Regex("""(//[^\n]*|#(?![\w-]*\{)[^\n]*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->)|("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`[^`]*`)|(\b\d+(?:\.\d+)?\b)""")

private fun highlight(code: String, c: ColorScheme) = buildAnnotatedString {
    append(code)
    KEYWORDS.findAll(code).forEach { addStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1) }
    TOKENS.findAll(code).forEach { m ->
        val style = when {
            m.groups[1] != null -> SpanStyle(color = c.outline, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal)
            m.groups[2] != null -> SpanStyle(color = c.tertiary, fontWeight = FontWeight.Normal)
            else -> SpanStyle(color = c.secondary)
        }
        addStyle(style, m.range.first, m.range.last + 1)
    }
}

@Composable
private fun TableBlock(rows: List<List<String>>) {
    val c = MaterialTheme.colorScheme
    val cols = rows.maxOfOrNull { it.size } ?: 0
    Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerLow) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp)) {
            rows.forEachIndexed { r, row ->
                Row(Modifier.clip(RoundedCornerShape(22.dp)).background(if (r == 0) c.secondaryContainer else Color.Transparent)) {
                    for (j in 0 until cols) Text(
                        inline(row.getOrElse(j) { "" }, c), Modifier.width(148.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        style = if (r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
