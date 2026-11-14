package com.agenda.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.agenda.app.design.*
import com.agenda.domain.*
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun AgendaApp(repository: AgendaViewModel,onDatasetChanged:()->Unit={}) {
    val data by repository.snapshot.collectAsState()
    val preferences by repository.preferences.collectAsState()
    val pendingDeletes by repository.pendingDeletions.collectAsState()
    val ready by repository.ready.collectAsState()
    val loadError by repository.loadError.collectAsState()
    val restored by repository.restoreApplied.collectAsState()
    val foreground by repository.foreground.collectAsState()
    val reminder by repository.dailyReminder.collectAsState()
    val backupWorking by repository.backupBusy.collectAsState()
    LaunchedEffect(restored) {if(restored) onDatasetChanged()}
    if(!ready) {
        Box(Modifier.fillMaxSize().background(Ink.Canvas).statusBarsPadding(),contentAlignment=Alignment.Center) {
            if(loadError==null) CircularProgressIndicator(color=Ink.Primary)
            else Column(horizontalAlignment=Alignment.CenterHorizontally) {
                AgendaText(loadError!!,color=Ink.Muted)
                TextButton({repository.reload()}) {AgendaText("重试",color=Ink.Primary)}
            }
        }
        return
    }
    val snackbar=remember {SnackbarHostState()}
    LaunchedEffect(repository) {repository.errors.collect {snackbar.showSnackbar(it)}}
    var page by rememberSaveable { mutableIntStateOf(preferences.page) }
    var board by rememberSaveable { mutableStateOf(preferences.board) }
    var fullDays by rememberSaveable { mutableStateOf(preferences.fullDays) }
    var overlay by rememberSaveable { mutableStateOf<String?>(null) }
    var focusObject by remember {mutableStateOf<String?>(null)}
    val pageStates=rememberSaveableStateHolder()
    LaunchedEffect(page,board,fullDays) {repository.savePreferences(UserPreferences(page,board,fullDays))}
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) { while(true) { today = LocalDate.now(); delay(15_000) } }
    BackHandler(page != 0 && overlay == null) { page = 0 }
    val backdrop = rememberGraphicsLayer()
    // Restore the route and its saved form only after a fresh backdrop is ready.
    var pendingOverlay by remember {mutableStateOf(overlay)}
    var preparedGlass by remember {mutableStateOf<GlassSnapshot?>(null)}
    val blurRadius=with(androidx.compose.ui.platform.LocalDensity.current) {9.dp.toPx()}
    fun showOverlay(target:String) {
        if(target.startsWith("focus-object:")) {
            focusObject=target.removePrefix("focus-object:");page=0;board=false
            overlay=null;pendingOverlay=null;preparedGlass=null;return
        }
        if(target=="search") focusObject=null
        if(overlay=="daily-reminder" && target!="daily-reminder") repository.dismissDailyReminder()
        if(overlay!=null && preparedGlass!=null) overlay=target
        else pendingOverlay=target
    }
    LaunchedEffect(foreground) {if(foreground) today=LocalDate.now()}
    LaunchedEffect(today,foreground,overlay,pendingOverlay,backupWorking) {
        if(foreground && overlay==null && pendingOverlay==null && !backupWorking) repository.checkDailyReminder(today)
    }
    LaunchedEffect(reminder,today,foreground,overlay,pendingOverlay,backupWorking,data.tasks) {
        val notice=reminder
        if(notice!=null && notice.first==today && foreground && overlay==null && pendingOverlay==null && !backupWorking) {
            if(reminderTasks(data.tasks,today).any {it.id in notice.second}) showOverlay("daily-reminder")
            else repository.dismissDailyReminder()
        }
    }
    BackHandler(pendingOverlay!=null) {pendingOverlay=null}
    LaunchedEffect(pendingOverlay) {
        val target=pendingOverlay ?: return@LaunchedEffect
        val started=android.os.SystemClock.elapsedRealtime()
        repeat(2) {withFrameNanos {}}
        val image=try {blurGlassSnapshot(backdrop.toImageBitmap(),blurRadius)}
        catch(c:kotlinx.coroutines.CancellationException) {throw c}
        catch(e:Exception) {
            android.util.Log.w("AgendaGlass","Backdrop preparation failed",e)
            GlassSnapshot(androidx.compose.ui.graphics.ImageBitmap(1,1),1,1)
        }
        preparedGlass=image
        overlay=target
        pendingOverlay=null
        if(BuildConfig.DEBUG) android.util.Log.d("AgendaGlassReady","${android.os.SystemClock.elapsedRealtime()}|$target|${android.os.SystemClock.elapsedRealtime()-started}")
    }
    Box(Modifier.fillMaxSize().background(Ink.Canvas)) {
        Column(Modifier.fillMaxSize().background(Ink.Canvas).drawWithContent {
            if(pendingOverlay!=null) {
                backdrop.record {
                    // The background modifier itself is outside drawContent().
                    // Seed an opaque canvas before capturing for raster blur.
                    drawRect(Ink.Canvas)
                    this@drawWithContent.drawContent()
                }
                drawLayer(backdrop)
            } else drawContent()
        }.then(if(overlay!=null) Modifier.clearAndSetSemantics {} else Modifier)
            .statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal=12.dp)) {
                if(page != 1) MarkButton(Mark.FLIP, if(page==0) "切换事项视图" else "切换日程视图", {
                    if(page==0) board = !board else fullDays = !fullDays
                },modifier=Modifier.align(Alignment.CenterStart))
                if(page==1) MarkButton(Mark.BACKUP,"备份与恢复",{showOverlay("backup")},modifier=Modifier.align(Alignment.CenterStart))
                AgendaText("Agenda", size=16,color=Ink.Primary,weight=FontWeight.SemiBold,
                    modifier=Modifier.align(Alignment.Center).semantics {contentDescription="Agenda品牌"})
                Row(Modifier.align(Alignment.CenterEnd)) {
                    if(page==2) MarkButton(Mark.WEEK, "周计划", {showOverlay("week")})
                    MarkButton(Mark.SEARCH,"搜索", {showOverlay("search")})
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                pageStates.SaveableStateProvider(page) { when(page) {
                    0 -> ThingsScene(data, repository, board, {showOverlay("task:$it")}, {showOverlay(if(listOf("material:","materials:","notes:","note:","schedule:","schedule-for:","edit-","delete:").any {prefix->it.startsWith(prefix)}) it else "info:$it")},focusObject)
                    1 -> ActionScene(data, repository, today, {showOverlay("task:$it")},{showOverlay("leisure")},{showOverlay("create")},{page=0})
                    else -> ScheduleScene(data, repository, today, fullDays, {showOverlay(if(it=="appearance" || it.startsWith("schedule:") || it.startsWith("edit-schedule:")) it else "info:$it")})
                } }
                if(page==0) FloatingActionButton(onClick={showOverlay("create")},
                    shape=CircleShape,containerColor=Ink.Primary,contentColor=Ink.Surface,
                    elevation=FloatingActionButtonDefaults.elevation(defaultElevation=4.dp),
                    modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp)
                        .semantics {contentDescription="新建任务"}) {AgendaIcon(Mark.PLUS,Ink.Surface)}
            }
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                listOf(Mark.GRAPH to "事项", Mark.ACTION to "行动", Mark.CALENDAR to "日程").forEachIndexed { index, (mark,label) ->
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        MarkButton(mark, label, {page=index}, if(page==index) Ink.Primary else Ink.Muted,
                            Modifier.semantics { selected = page==index })
                        Box(Modifier.size(5.dp).background(if(page==index) Ink.Teal else Ink.Divider))
                    }
                }
            }
        }
        if(pendingOverlay!=null) Box(Modifier.matchParentSize().clickable(
            interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null,onClick={}))
        if(overlay?.startsWith("image:")==true) {
            val imageId=overlay!!.removePrefix("image:")
            MaterialImageViewer(data.objects.flatMap {it.materials}.find {it.id==imageId},repository) {overlay="material:$imageId"}
        } else if(overlay != null && preparedGlass!=null) key(overlay) { GlassOverlay(overlay!!, data, repository, preparedGlass!!,
            {if(overlay=="daily-reminder") repository.dismissDailyReminder();overlay=null;preparedGlass=null}, {showOverlay(it)}) }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).padding(horizontal=16.dp).padding(bottom=(80+pendingDeletes.size.coerceAtMost(3)*60).dp))
        UndoDeletionNotices(repository,Modifier.align(Alignment.BottomCenter).imePadding().padding(horizontal=12.dp).padding(bottom=82.dp))
    }
}

@Composable
fun TaskLine(task: Task, repository: AgendaViewModel, onOpen: (String)->Unit, date: Boolean = false, enabled:Boolean=true,onAccent:Boolean=false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
        Checkbox(task.completed, if(enabled) ({done:Boolean->repository.setCompleted(task.id,done)}) else null, modifier=Modifier.semantics {
            contentDescription = "${if(task.completed) "恢复未完成" else "完成"}：${task.name}"
        }, colors=CheckboxDefaults.colors(checkedColor=Ink.Primary, uncheckedColor=Ink.Control))
        Column(Modifier.weight(1f).clickable(enabled=enabled) {onOpen(task.id)}.padding(vertical=10.dp)) {
            AgendaText(task.name, color=if(task.completed) Ink.Muted else Ink.Text)
            val due=task.due
            if(date && due!=null) AgendaText("${due.monthValue} 月 ${due.dayOfMonth} 日截止", size=12, color=if(onAccent) Ink.Text else Ink.Muted)
        }
    }
}

@Composable
private fun ActionScene(data: AgendaSnapshot, repository: AgendaViewModel, today: LocalDate, onOpen:(String)->Unit,onLeisure:()->Unit,onCreate:()->Unit,onThings:()->Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(expanded!=null) { expanded=null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=26.dp,bottom=24.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        AgendaText("现在，适合做什么", title=true,size=20,color=Ink.Primary)
        Spacer(Modifier.height(4.dp))
        val urgent = urgentTasks(data.tasks,today,limit=1).firstOrNull()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Teal)
            .animateContentSize().padding(16.dp)) {
            AgendaText("尽快做",title=true,size=19,color=Ink.Text)
            if(urgent==null) AgendaText("一周内暂无待完成任务，按自己的节奏来。",color=Ink.Text,
                modifier=Modifier.padding(top=12.dp,bottom=8.dp))
            else {
                TaskLine(urgent,repository,onOpen,date=true,onAccent=true)
                if(urgent.description.isNotBlank()) AgendaText(urgent.description,size=13,color=Ink.Text,maxLines=2,
                    modifier=Modifier.padding(start=12.dp,bottom=8.dp).clickable {onOpen(urgent.id)})
            }
        }
        val labels = Effort.entries.map {it.label} + "休闲活动"
        labels.chunked(2).forEach {pair->
            ExpandablePair(pair,expanded,{expanded=if(expanded==it) null else it},
                label={"行动卡片：$it"},baseHeight=148.dp,
                header={label->
                    AgendaText(label,title=true,size=19,color=Ink.Primary)
                    val tasks=data.tasks.filter {it.effort?.label==label && !it.completed}
                    Spacer(Modifier.height(10.dp))
                    AgendaText(if(label=="休闲活动") "${data.leisure.size} 件喜欢的事" else "${tasks.size} 项待做",size=12,color=Ink.Muted)
                },body={label,interactive->
                    Column {
                        val tasks=data.tasks.filter {it.effort?.label==label && !it.completed}
                        if(label=="休闲活动") {
                            data.leisure.forEach {AgendaText(it.text,size=13,modifier=Modifier.padding(vertical=6.dp))}
                            AgendaText("也可以，什么都不做。",size=13,color=Ink.Muted)
                            TextButton(onLeisure,enabled=interactive) {AgendaText("管理休闲活动",size=12,color=Ink.Primary)}
                        }
                        else if(tasks.isEmpty()) {
                            val allDone=data.tasks.any {it.effort?.label==label}
                            AgendaText(if(allDone) "这一类任务都完成了" else "还没有这类任务",size=13,color=Ink.Muted)
                            TextButton(if(allDone) onThings else onCreate,enabled=interactive) {
                                AgendaText(if(allDone) "查看事项" else "新建任务",size=12,color=Ink.Primary)
                            }
                        }
                        else tasks.forEach {TaskLine(it,repository,onOpen,enabled=interactive)}
                    }
                })
        }

    }
}

