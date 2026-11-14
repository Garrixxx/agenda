package com.agenda.app.design

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.agenda.app.R

object Ink {
    val Canvas = Color(0xFFEEEEEE)
    val Surface = Color(0xFFFAFAFA)
    val Text = Color(0xFF222831)
    val Muted = Color(0xFF59616B)
    val Primary = Color(0xFF3B4F62)
    val Teal = Color(0xFF00ADB5)
    val Control = Color(0xFF00767C)
    val CardBorder = Color(0xFFC4D0D9)
    val Connection = Color(0xFF8798A5)
    val Divider = Color(0xFFCDD2D6)
    val Done = Color(0xFF376C60)
    val ObjectFill = Color(0xFFDFE4E7)
    val Danger = Color(0xFFA13E36)
}
val BodyFont = FontFamily(Font(R.font.notosanssc))
private val TitleFont = FontFamily(Font(R.font.notoserifsc))
private val LatinFont = FontFamily(Font(R.font.spacegrotesk),Font(R.font.spacegrotesk_semibold,FontWeight.SemiBold))
private val NumberFont = FontFamily(Font(R.font.manrope))

@Composable
fun AgendaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink.Primary, onPrimary = Ink.Surface,
        surface = Ink.Surface, onSurface = Ink.Text, background = Ink.Canvas,
        onBackground = Ink.Text, outline = Ink.Divider, secondary = Ink.Teal), content = content)
}

@Composable
fun AgendaText(text: String, modifier: Modifier = Modifier, title: Boolean = false,
    size: Int = 14, color: Color = Ink.Text, maxLines: Int = Int.MAX_VALUE,
    weight: FontWeight = FontWeight.Normal, decoration: TextDecoration? = null) {
    // Android has no CSS unicode-range: explicitly route Latin letters and digits in each run.
    val mixed = agendaAnnotated(text,title)
    Text(mixed, modifier, color = color, fontSize = readingSize(size).sp, lineHeight = (readingSize(size) * 1.5).sp,
        fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFeatureSettings = "tnum", textDecoration=decoration))
}

private fun readingSize(size:Int):Int = when(size) {
    11 -> 12; 12 -> 13; 13 -> 14; 14 -> 16; 18 -> 20; 19 -> 21; 20 -> 24
    else -> size
}

fun agendaAnnotated(text:String, title:Boolean=false): AnnotatedString = buildAnnotatedString {
        text.forEach { ch ->
            val family = when {
                ch in 'A'..'Z' || ch in 'a'..'z' -> LatinFont
                ch in '0'..'9' || ch.code in 0x21..0x40 || ch.code in 0x5b..0x60 -> NumberFont
                else -> if (title) TitleFont else BodyFont
            }
            withStyle(SpanStyle(fontFamily = family)) { append(ch) }
        }
}
