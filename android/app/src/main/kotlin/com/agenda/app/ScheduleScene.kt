package com.agenda.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.agenda.app.design.*
import com.agenda.domain.*
import java.time.LocalDate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged

private fun weekday(date:LocalDate) = listOf("周一","周二","周三","周四","周五","周六","周日")[date.dayOfWeek.value-1]

@Composable
fun ScheduleScene(data:AgendaSnapshot,repository:AgendaViewModel,today:LocalDate,full:Boolean,onInfo:(String)->Unit) {
    var backgroundFailed by remember(data.appearance.assetId) {mutableStateOf(false)}
    val background by produceState<ImageBitmap?>(null,data.appearance.assetId) {
        value=null;backgroundFailed=false
        data.appearance.assetId?.let {id->
            try {value=repository.appearanceImage(id).asImageBitmap()}
            catch(c:kotlinx.coroutines.CancellationException) {throw c}
            catch(e:Exception) {backgroundFailed=true}
        }
    }
    var selected by rememberSaveable(today) {mutableStateOf<LocalDate?>(null)}
    val dates=remember(today) {(-3L..14L).map {today.plusDays(it)}}
    val stripDensity=LocalDensity.current
    val stripState=rememberScrollState(initial=with(stripDensity) {82.dp.roundToPx()})
    var focusedDay by rememberSaveable {mutableStateOf<LocalDate?>(null)}
    val fullListState=rememberLazyListState(initialFirstVisibleItemIndex=3)
    BackHandler(selected!=null && !full) {selected=null}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(if(data.appearance.assetId==null) Color(data.appearance.color) else Ink.Canvas))
        background?.let {Image(it,contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.matchParentSize().graphicsLayer {alpha=.20f})}
        val fullWidth=maxWidth-32.dp
        val density=LocalDensity.current
        val expandedWidth=fullWidth*.87f
        LaunchedEffect(selected,full) {
            val day=selected ?: focusedDay
            if(day!=null && !full) {
                val index=dates.indexOf(day)
                if(index>=0) {
                    val target=with(density) {
                        (20.dp+82.dp*index+(if(selected==day) expandedWidth else 70.dp)/2-maxWidth/2).roundToPx()
                    }
                    // One coordinated movement. No delayed second centring pass.
                    stripState.animateScrollTo(target.coerceAtLeast(0),AgendaMotion.container())
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(top=maxHeight*.08f)) {
            Column(Modifier.padding(horizontal=20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    AgendaText("${today.year} 年 ${today.monthValue} 月",size=13,color=Ink.Primary,modifier=Modifier.weight(1f))
                    TextButton({onInfo("appearance")}) {AgendaText("背景与文字",size=12,color=Ink.Primary)}
                }
                if(backgroundFailed) AgendaText("背景图片暂不可用，可重新选择",size=12,color=Ink.Danger)
                AgendaText("今天，留一点余地。",title=true,size=20,color=Ink.Primary,modifier=Modifier.padding(top=4.dp,bottom=22.dp))
            }
            if(full) {
                LazyRow(state=fullListState,flingBehavior=rememberSnapFlingBehavior(fullListState),
                    contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f)) {
                    items(dates,key={it.toString()}) {day->
                        Column(Modifier.width(fullWidth).fillMaxHeight().padding(bottom=12.dp)
                            .clip(RoundedCornerShape(16.dp)).border(1.dp,Ink.Divider.copy(alpha=.65f),RoundedCornerShape(16.dp))
                            .background(Ink.Surface).verticalScroll(rememberScrollState()).padding(16.dp)) {
                            DayHeading(day,today,data)
                            HorizontalLine()
                            DayContent(day,data,repository,onInfo)
                        }
                    }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val dateAreaHeight=maxHeight
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Divider))
                    // A finite strip keeps compact dates composed. Lazy composition
                    // during shrinking used to insert several expensive detail trees.
                    val viewportWidth=with(density) {this@BoxWithConstraints.maxWidth.roundToPx()}
                    Layout(modifier=Modifier.fillMaxSize().horizontalScroll(stripState).padding(horizontal=20.dp),content={
                        dates.forEach {day->
                            key(day) {
                                HangingDateCard(day,today,data,repository,selected==day,expandedWidth,
                                    dateAreaHeight,
                                    {focusedDay=day;selected=if(selected==day) null else day},onInfo)
                            }
                        }
                    }) {measurables,constraints->
                        val gap=12.dp.roundToPx()
                        val cards=measurables.map {it.measure(Constraints(maxHeight=constraints.maxHeight))}
                        layout(cards.sumOf {it.width}+gap*(cards.size-1),constraints.maxHeight) {
                            // Keep cheap compact trees warm, but never traverse or draw
                            // the sixteen offscreen dates on each animation frame.
                            val first=stripState.value-20.dp.roundToPx()
                            val last=first+viewportWidth
                            var x=0
                            cards.forEach {card->
                                if(x+card.width>=first-gap && x<=last+gap) card.placeRelative(x,0)
                                x+=card.width+gap
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(selected==null,enter=fadeIn(tween(120,delayMillis=320)),exit=fadeOut(tween(70)),
                        modifier=Modifier.align(Alignment.Center).padding(top=80.dp,start=45.dp,end=45.dp)) {
                        Box(Modifier.fillMaxWidth()
                            .clickable {onInfo("appearance")},contentAlignment=Alignment.Center) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                SchedulePlant()
                                if(data.appearance.text.isNotBlank()) AgendaText(data.appearance.text,title=true,size=18,color=Ink.Primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HangingDateCard(day:LocalDate,today:LocalDate,data:AgendaSnapshot,repository:AgendaViewModel,
    open:Boolean,expandedWidth:Dp,maxHeight:Dp,onToggle:()->Unit,onInfo:(String)->Unit) {
    val progress=animateFloatAsState(if(open) 1f else 0f,AgendaMotion.container(),label="hanging-container")
    val interactive by remember(open) {derivedStateOf {open && progress.value>.96f}}
    val retainDetails by remember(open) {derivedStateOf {open || progress.value>.001f}}
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.width(1.dp).height(14.dp).background(Ink.Divider))
        Box(Modifier.clip(RoundedCornerShape(16.dp)).drawBehind {
                drawRect(if(day==today) androidx.compose.ui.graphics.lerp(Ink.Teal,Ink.Surface,progress.value.coerceIn(0f,1f)) else Ink.Surface)
            }.onSizeChanged {
                if(BuildConfig.DEBUG) android.util.Log.d("AgendaDateMotion","${android.os.SystemClock.elapsedRealtime()}|$day|${it.width}|${it.height}")
            }.clickable(onClick=onToggle).semantics {contentDescription="${if(open) "收起" else "展开"}日期：$day"}) {
            Layout(content={
                Column(Modifier.graphicsLayer {alpha=(1f-progress.value*3).coerceIn(0f,1f)}
                    .clearAndSetSemantics {}.padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    AgendaText(weekday(day),size=12,color=Ink.Muted)
                    AgendaText("${day.dayOfMonth}",size=27,modifier=Modifier.padding(vertical=4.dp))
                    AgendaText("${day.monthValue} 月",size=11,color=Ink.Muted)
                    Row(Modifier.padding(top=10.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        repeat(data.schedule.count {it.date==day}.coerceIn(1,4)) {
                            Box(Modifier.size(3.dp).background(if(data.schedule.any {it.date==day}) Ink.Primary else Ink.Divider))
                        }
                    }
                }
                if(retainDetails) Column(Modifier.heightIn(max=maxHeight).verticalScroll(rememberScrollState())
                    .graphicsLayer {alpha=((progress.value-.2f)/.6f).coerceIn(0f,1f)}
                    .then(if(!interactive) Modifier.clearAndSetSemantics {} else Modifier).padding(16.dp)) {
                    DisposableEffect(day) {
                        if(BuildConfig.DEBUG) android.util.Log.d("AgendaDateDetails","${android.os.SystemClock.elapsedRealtime()}|$day|composed")
                        onDispose {}
                    }
                    DayHeading(day,today,data);HorizontalLine()
                    DayContent(day,data,repository,onInfo,enabled=interactive)
                }
            }) {measurables,_->
                val compact=measurables[0].measure(Constraints.fixedWidth(70.dp.roundToPx()))
                val full=measurables.getOrNull(1)?.measure(Constraints.fixedWidth(expandedWidth.roundToPx()))
                val v=progress.value.coerceIn(0f,1.015f)
                val w=(compact.width+(expandedWidth.roundToPx()-compact.width)*v).toInt()
                val h=(compact.height+((full?.height ?: compact.height)-compact.height)*v).toInt()
                layout(w,h) {compact.placeRelative(0,0);full?.placeRelative(0,0)}
            }
            if(!interactive) Box(Modifier.matchParentSize().clickable(onClick=onToggle))
        }
    }
}

@Composable
private fun DayHeading(day:LocalDate,today:LocalDate,data:AgendaSnapshot) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        AgendaText("${day.dayOfMonth}",size=38,color=Ink.Primary)
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            AgendaText("${day.monthValue} 月 / ${weekday(day)}",size=12,color=Ink.Muted)
            if(day==today) AgendaText("今天",size=12,color=Ink.Control)
        }
        AgendaText("${data.schedule.count {it.date==day}} 项",size=12,color=Ink.Muted)
    }
}

@Composable
private fun DayContent(day:LocalDate,data:AgendaSnapshot,repository:AgendaViewModel,onInfo:(String)->Unit,enabled:Boolean=true) {
    Period.entries.forEach {period->
        Column(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                AgendaText(period.label,size=13,color=Ink.Muted,modifier=Modifier.weight(1f))
                MarkButton(Mark.PLUS,"${day.monthValue}月${day.dayOfMonth}日${period.label}添加日程",{
                    onInfo("schedule:$day:${period.name}")
                })
            }
            val entries=data.schedule.filter {it.date==day && it.period==period}
            if(entries.isEmpty()) AgendaText("留白",size=13,color=Ink.Muted,modifier=Modifier.padding(start=8.dp,bottom=8.dp))
            entries.forEach {entry->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(entry.isCompleted(data.tasks),if(enabled) ({done:Boolean->repository.setScheduleCompleted(entry.id,done)}) else null,
                        modifier=Modifier.semantics { contentDescription="完成：${entry.name(data.tasks)}" },
                        colors=CheckboxDefaults.colors(checkedColor=Ink.Primary,uncheckedColor=Ink.Control))
                    Column(Modifier.weight(1f).clickable(enabled=enabled) {onInfo("edit-schedule:${entry.id}")}
                        .semantics {contentDescription="编辑日程：${entry.name(data.tasks)}"}.padding(vertical=8.dp)) {
                        AgendaText(entry.name(data.tasks),color=if(entry.isCompleted(data.tasks)) Ink.Muted else Ink.Text)
                        entry.time?.let {AgendaText(it.toString(),size=12,color=Ink.Muted)}
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalLine() { Box(Modifier.fillMaxWidth().padding(vertical=12.dp).height(1.dp).background(Ink.Divider)) }

