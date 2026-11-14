package com.agenda.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.agenda.app.design.Ink

/** Small botanical line drawing matching the supplied reference. Decorative only. */
@Composable
fun SchedulePlant() {
    Canvas(Modifier.width(156.dp).height(123.dp).padding(bottom=16.dp)) {
        // Keep the reference's 156:123 coordinates intact at every density.
        val factor=minOf(size.width/156f,size.height/123f)
        withTransform({
            translate((size.width-156f*factor)/2,(size.height-123f*factor)/2)
            scale(factor,factor,Offset.Zero)
        }) {
            val stroke=Stroke(1.35f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            fun curve(draw:Path.()->Unit) {drawPath(Path().apply(draw),Ink.Primary,style=stroke)}
            curve {moveTo(17f,104f);cubicTo(59f,105f,103f,103f,138f,100f)}
            // Every leaf closes at an actual point on this continuous stem.
            curve {moveTo(58f,102f);cubicTo(67f,85f,76f,68f,82f,54f)}
            curve {moveTo(64.3f,90f);cubicTo(54f,92f,47f,87f,43f,79f)
                cubicTo(53f,77f,63f,81f,64.3f,90f);close()}
            curve {moveTo(71.1f,76.6f);cubicTo(60f,76f,54f,69f,54f,61f)
                cubicTo(64f,60f,71f,66f,71.1f,76.6f);close()}
            curve {moveTo(75.2f,68.2f);cubicTo(84f,71f,95f,66f,101f,57f)
                cubicTo(91f,55f,79f,58f,75.2f,68.2f);close()}
            curve {moveTo(82f,54f);cubicTo(77f,50f,78f,44f,83f,39f)
                cubicTo(87f,45f,88f,51f,82f,54f);close()}
            drawCircle(Ink.Primary,8f,Offset(115f,51f),style=stroke)
        }
    }
}
