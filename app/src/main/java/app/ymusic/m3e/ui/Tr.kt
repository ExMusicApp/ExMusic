@file:OptIn(ExperimentalMaterial3Api::class)

package app.ymusic.m3e.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import app.ymusic.m3e.R

/**
 * Язык интерфейса: русский (исходный), английский, белорусский. Переводы — в res/raw/translations.tsv
 * («русский ⇥ английский ⇥ белорусский»). Строки, которых в словаре нет, остаются русскими.
 */
internal object AppLang {
    private var prefs: android.content.SharedPreferences? = null
    private var en: Map<String, String> = emptyMap()
    private var be: Map<String, String> = emptyMap()
    private var patEn: List<Pat> = emptyList()
    private var patBe: List<Pat> = emptyList()
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Строка-шаблон словаря с «дырками» {} (для сообщений с подставленными названиями и числами). */
    private class Pat(val prefix: String, val suffix: String, val re: Regex, val out: String, val literalLen: Int) {
        companion object {
            fun of(key: String, out: String): Pat {
                val parts = key.split("{}")
                val re = Regex(parts.joinToString("(.+?)") { Regex.escape(it) }.let { "^$it$" }, RegexOption.DOT_MATCHES_ALL)
                return Pat(parts.first(), parts.last(), re, out, parts.sumOf { it.length })
            }
        }
        fun apply(s: String): String? {
            if (!s.startsWith(prefix) || !s.endsWith(suffix)) return null
            val m = re.matchEntire(s) ?: return null
            var i = 1
            return Regex("\\{\\}").replace(out) { m.groupValues.getOrElse(i++) { "" } }
        }
    }

    /** "ru" / "en" / "be". Меняется состоянием — интерфейс перерисовывается сразу. */
    var code by mutableStateOf("ru")
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = app.getSharedPreferences("lang", Context.MODE_PRIVATE)
        code = prefs?.getString("code", "ru")?.takeIf { it in listOf("ru", "en", "be") } ?: "ru"
        runCatching {
            val e = HashMap<String, String>()
            val b = HashMap<String, String>()
            val pe = ArrayList<Pat>()
            val pb = ArrayList<Pat>()
            app.resources.openRawResource(R.raw.translations).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { l ->
                    val p = l.split('\t')
                    if (p.size >= 3) {
                        val k = p[0].replace("\\n", "\n")
                        val te = p[1].replace("\\n", "\n")
                        val tb = p[2].replace("\\n", "\n")
                        if (k.contains("{}")) {
                            // Шаблон: «Скачано: {}» подходит под «Скачано: Трек» — вместо {} подставляется найденный кусок.
                            pe.add(Pat.of(k, te)); pb.add(Pat.of(k, tb))
                        } else { e[k] = te; b[k] = tb }
                    }
                }
            }
            en = e; be = b
            patEn = pe.sortedByDescending { it.literalLen }
            patBe = pb.sortedByDescending { it.literalLen }
            cache.clear()
        }
    }

    fun set(c: String) {
        code = c
        prefs?.edit()?.putString("code", c)?.apply()
    }

    fun title(c: String) = when (c) { "en" -> "English"; "be" -> "Беларуская"; else -> "Русский" }

    /** Перевод строки интерфейса; цифры в строке не мешают («5 треков» → «5 tracks» через ключ «# …»). */
    fun tr(s: String): String {
        val c = code
        if (c == "ru" || s.isEmpty()) return s
        val map = if (c == "en") en else be
        map[s]?.let { return it }
        if (s.any { it.isDigit() }) {
            val digits = Regex("\\d+")
            val nums = digits.findAll(s).map { it.value }.toList()
            map[digits.replace(s, "#")]?.let { t ->
                var i = 0
                return Regex("#").replace(t) { nums.getOrElse(i++) { "#" } }
            }
        }
        // Шаблоны с {}: результат запоминаем, чтобы длинный список шаблонов не гонять по одной и той же строке.
        if (s.none { it in 'А'..'я' || it == 'Ё' || it == 'ё' }) return s
        val ck = c + "\u0000" + s
        cache[ck]?.let { return it }
        var res = s
        for (p in if (c == "en") patEn else patBe) {
            val r = p.apply(s)
            if (r != null) { res = r; break }
        }
        if (cache.size > 4000) cache.clear()
        cache[ck] = res
        return res
    }
}

// ---- Text: все Text(...) в пакете ui берут перевод из словаря (явный пакетный Text приоритетнее material3.*) ----

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // Пункт, выбранный в поиске по настройкам, ненадолго подсвечивается и прокручивается в видимую область.
    val flash = SettingsHighlight.item == text
    androidx.compose.material3.Text(
        text = AppLang.tr(text), modifier = if (flash) modifier.searchFlash() else modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
internal fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
    )
}

/**
 * Текст для плиток и кнопок: сначала переносится на новую строку (до [maxLines]), если всё равно не помещается —
 * шрифт уменьшается (до [minScale]), и только потом лишнее обрезается с многоточием.
 */
@Composable
internal fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 2,
    minScale: Float = 0.7f,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val shown = AppLang.tr(text)
    var scale by remember(shown, style) { mutableFloatStateOf(1f) }
    var wrapped by remember(shown, style) { mutableStateOf(false) }
    val base = style.fontSize
    val fs = if (base.isSpecified) base * scale else base
    val lh = if (wrapped && base.isSpecified) fs * 1.12f else if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
    androidx.compose.material3.Text(
        text = shown, modifier = modifier, color = color, fontWeight = fontWeight, textAlign = textAlign,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = style.copy(fontSize = fs, lineHeight = lh),
        onTextLayout = { r ->
            if (r.lineCount > 1 && !wrapped) {
                // Не уместилось в одну строку: переносим, чуть уменьшив шрифт, чтобы две строки влезли в высоту кнопки.
                wrapped = true
                if (scale > 0.86f) scale = 0.86f
            } else if (r.hasVisualOverflow && scale > minScale) {
                scale = (scale * 0.92f).coerceAtLeast(minScale)
            }
        },
    )
}

/** Пункт «Язык» в настройках: открывает окно выбора русского / английского / белорусского. */
@Composable
internal fun LanguageSettingItem(colors: ListItemColors) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("Язык") },
        supportingContent = { androidx.compose.material3.Text(AppLang.title(AppLang.code)) },
        leadingContent = { Icon(painterResource(R.drawable.ms_translate), null) },
        trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
        colors = colors,
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Язык") },
            text = {
                Column {
                    listOf("ru", "en", "be").forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { AppLang.set(c); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = AppLang.code == c, onClick = { AppLang.set(c); open = false })
                            androidx.compose.material3.Text(AppLang.title(c), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } },
        )
    }
}
