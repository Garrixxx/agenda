package com.agenda.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.agenda.app.design.Ink

/** Small botanical line drawing matching the supplied reference. Decorative only. */
@Composable
fun SchedulePlant() {
    Canvas(Modifier.width(156.dp).height(120.dp).padding(bottom=16.dp)) {
        val factor=size.width/156f
        scale(factor,factor,pivot=Offset.Zero) {
            val stroke=Stroke(1.5f)
            fun curve(draw:Path.()->Unit) {drawPath(Path().apply(draw),Ink.Primary,style=stroke)}
            curve {moveTo(16f,100f);cubicTo(58f,101f,106f,99f,139f,96f)}
            curve {moveTo(57f,99f);cubicTo(62f,79f,73f,55f,83f,35f)}
            curve {moveTo(63f,85f);cubicTo(52f,88f,47f,80f,42f,75f);cubicTo(53f,74f,61f,76f,63f,85f)}
            curve {moveTo(71f,68f);cubicTo(60f,69f,54f,59f,53f,56f);cubicTo(63f,55f,69f,59f,71f,68f)}
            curve {moveTo(75f,62f);cubicTo(88f,65f,96f,57f,101f,51f);cubicTo(89f,50f,80f,52f,75f,62f)}
            curve {moveTo(82f,49f);cubicTo(76f,44f,78f,36f,83f,33f);cubicTo(88f,39f,88f,44f,82f,49f)}
            drawCircle(Ink.Primary,8f,Offset(115f,46f),style=stroke)
        }
    }
}
