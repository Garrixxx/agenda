package com.agenda.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.agenda.app.design.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun BackupPanel(repository:AgendaViewModel,onBusy:(Boolean)->Unit) {
    val busy by repository.backupBusy.collectAsState()
    val progress by repository.backupProgress.collectAsState()
    val error by repository.backupError.collectAsState()
    val status by repository.backupStatus.collectAsState()
    val prepared by repository.preparedBackup.collectAsState()
    val snapshots by repository.recoverySnapshots.collectAsState()
    var snapshotExport by rememberSaveable {mutableStateOf<String?>(null)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {uri->
        if(uri!=null) repository.exportBackup(uri,snapshotExport)
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) repository.prepareBackup(uri)}
    SideEffect {onBusy(busy)}
    LaunchedEffect(Unit) {repository.refreshRecoverySnapshots()}
    AgendaText("导出本机数据、材料副本和设置。恢复会整体替换，不合并。",size=13,color=Ink.Muted,modifier=Modifier.padding(top=12.dp,bottom=14.dp))
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button({snapshotExport=null;export.launch("Agenda-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.agenda.zip")},enabled=!busy,shape=RoundedCornerShape(11.dp)) {AgendaText("导出完整备份",color=Ink.Surface,size=13)}
        OutlinedButton({open.launch(arrayOf("*/*"))},enabled=!busy,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,Ink.Divider)) {AgendaText("选择备份恢复",size=13)}
    }
    if(busy) Row(Modifier.padding(vertical=16.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp),color=Ink.Primary,strokeWidth=2.dp)
        AgendaText(progress.ifBlank {"正在处理…"},size=13,color=Ink.Primary,modifier=Modifier.padding(start=10.dp).semantics {liveRegion=LiveRegionMode.Polite})
    }
    status?.let {AgendaText(it,size=13,color=Ink.Primary,modifier=Modifier.padding(top=12.dp).semantics {liveRegion=LiveRegionMode.Polite})}
    error?.let {AgendaText(it,size=13,color=Ink.Danger,modifier=Modifier.padding(top=12.dp).semantics {liveRegion=LiveRegionMode.Polite})}
    prepared?.let {value->
        HorizontalDivider(Modifier.padding(vertical=16.dp),color=Ink.Divider)
        AgendaText("已校验的备份",title=true,size=17,color=Ink.Primary)
        val info=value.info
        AgendaText("含 ${info.materialLinks} 条任务与材料关联及任务说明",size=12,color=Ink.Muted,modifier=Modifier.padding(top=6.dp))
        val backupTime=Instant.ofEpochMilli(info.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        AgendaText("备份时间：$backupTime",size=12,color=Ink.Muted,modifier=Modifier.padding(top=6.dp))
        AgendaText("${info.objects} 个对象 · ${info.tasks} 项任务 · ${info.materials} 份材料\n${info.notes} 份说明 · ${info.schedule} 项日程 · ${info.weekly} 条周计划",
            size=13,modifier=Modifier.padding(vertical=10.dp))
        AgendaText("${info.leisure} 项休闲活动 · ${info.backgrounds} 张背景图片，含已保存布局及提醒记录",size=12,color=Ink.Muted,modifier=Modifier.padding(bottom=10.dp))
        AgendaText("确认后将替换当前数据，并先自动保存恢复前快照。",size=13,color=Ink.Muted)
        Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton({repository.cancelPreparedBackup()},enabled=!busy) {AgendaText("取消恢复",size=13,color=Ink.Primary)}
            Button({repository.restoreBackup()},enabled=!busy,shape=RoundedCornerShape(11.dp)) {AgendaText("恢复并替换当前数据",size=13,color=Ink.Surface)}
        }
    }
    HorizontalDivider(Modifier.padding(vertical=16.dp),color=Ink.Divider)
    AgendaText("恢复前快照",title=true,size=17,color=Ink.Primary)
    AgendaText("自动保留最近三份，可恢复或另存到手机。",size=12,color=Ink.Muted,modifier=Modifier.padding(top=6.dp,bottom=8.dp))
    if(snapshots.isEmpty()) AgendaText("还没有恢复前快照",size=13,color=Ink.Muted,modifier=Modifier.padding(vertical=8.dp))
    snapshots.forEach {snapshot->
        val time=Instant.ofEpochMilli(snapshot.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
            AgendaText(time,size=13)
            Row {
                TextButton({repository.prepareRecoverySnapshot(snapshot.id)},enabled=!busy,modifier=Modifier.semantics {contentDescription="恢复快照：${snapshot.id}"}) {AgendaText("恢复这份快照",size=12,color=Ink.Primary)}
                TextButton({snapshotExport=snapshot.id;export.launch("Agenda-before-restore-$time.agenda.zip".replace(':','-'))},enabled=!busy,
                    modifier=Modifier.semantics {contentDescription="导出快照：${snapshot.id}"}) {AgendaText("导出",size=12,color=Ink.Primary)}
            }
        }
    }
}
