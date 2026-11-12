package com.agenda.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.agenda.app.design.*
import com.agenda.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LeisurePanel(data:AgendaSnapshot,repository:AgendaViewModel,onNavigate:(String)->Unit,onDirty:(Boolean)->Unit,onBusy:(Boolean)->Unit) {
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    var text by rememberSaveable {mutableStateOf("")}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val original=data.leisure.find {it.id==editing}?.text.orEmpty()
    val dirty=text!=original
    SideEffect {onDirty(dirty);onBusy(saving)}
    AgendaText("记下自己喜欢做的事，也可以什么都不做。",size=13,color=Ink.Muted,modifier=Modifier.padding(vertical=12.dp))
    OutlinedTextField(text,{text=it;error=null},label={AgendaText(if(editing==null) "新的休闲活动" else "编辑休闲活动",size=12)},
        modifier=Modifier.fillMaxWidth().semantics {contentDescription="休闲活动内容"},enabled=!saving,singleLine=true,shape=RoundedCornerShape(14.dp),isError=error!=null)
    Row(Modifier.padding(top=10.dp)) {
        Button({
            if(text.isBlank()) error="请先填写休闲活动"
            else {val id=editing;val value=text;saving=true;scope.launch {
                try {repository.saveLeisure(id,value);editing=null;text="";error=null}
                catch(c:CancellationException) {throw c}
                catch(e:Exception) {error=saveError(e)}
                finally {saving=false}
            }}
        },enabled=!saving,shape=RoundedCornerShape(11.dp)) {AgendaText(if(saving) "保存中…" else "保存活动",color=Ink.Surface)}
        if(editing!=null || text.isNotEmpty()) TextButton({editing=null;text="";error=null},enabled=!saving) {AgendaText("取消编辑",size=13,color=Ink.Primary)}
    }
    error?.let {AgendaText(it,size=12,color=Ink.Danger,modifier=Modifier.padding(top=8.dp).semantics {liveRegion=LiveRegionMode.Polite})}
    HorizontalDivider(Modifier.padding(vertical=16.dp),color=Ink.Divider)
    if(data.leisure.isEmpty()) AgendaText("还没有记录喜欢的事",size=13,color=Ink.Muted)
    data.leisure.forEach {item->Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
        AgendaText(item.text,size=14,modifier=Modifier.weight(1f))
        TextButton({editing=item.id;text=item.text;error=null},enabled=!saving && !dirty,
            modifier=Modifier.semantics {contentDescription="编辑休闲活动：${item.text}"}) {AgendaText("编辑",size=12,color=Ink.Primary)}
        TextButton({onNavigate("delete:leisure:${item.id}")},enabled=!saving && !dirty,
            modifier=Modifier.semantics {contentDescription="删除休闲活动：${item.text}"}) {AgendaText("删除",size=12,color=Ink.Danger)}
    }}
    AgendaText("不计入任务，也没有完成打卡要求。",size=12,color=Ink.Muted,modifier=Modifier.padding(top=14.dp))
}

@Composable
internal fun AppearancePanel(data:AgendaSnapshot,repository:AgendaViewModel,onClose:()->Unit,onDirty:(Boolean)->Unit,onBusy:(Boolean)->Unit) {
    val original=remember {data.appearance}
    var color by rememberSaveable {mutableLongStateOf(original.color)}
    var text by rememberSaveable {mutableStateOf(original.text)}
    var useImage by rememberSaveable {mutableStateOf(original.assetId!=null)}
    var selectedUri by rememberSaveable {mutableStateOf<String?>(null)}
    var saving by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->if(uri!=null) {selectedUri=uri.toString();useImage=true;error=null}}
    SideEffect {onDirty(color!=original.color || text!=original.text || useImage!=(original.assetId!=null) || (useImage && selectedUri!=null));onBusy(saving)}
    AgendaText("日程页背景",size=13,color=Ink.Muted,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        OptionChip("纯色",!useImage,{if(!saving) useImage=false},Modifier.weight(1f))
        OptionChip("图片",useImage,{if(!saving) {if(original.assetId!=null || selectedUri!=null) useImage=true else picker.launch(arrayOf("image/*"))}},Modifier.weight(1f))
    }
    if(useImage) {
        AgendaText(if(selectedUri!=null) "已选择新图片，保存后生效" else "使用已保存的图片副本",size=13,color=Ink.Muted,modifier=Modifier.padding(vertical=12.dp))
        TextButton({picker.launch(arrayOf("image/*"))},enabled=!saving) {AgendaText("更换背景图片",size=13,color=Ink.Primary)}
        AgendaText("图片会保存在应用内，并以浅色显示以便阅读。",size=12,color=Ink.Muted)
    } else Row(Modifier.padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("浅灰" to 0xFFEEEEEEL,"近白" to 0xFFFAFAFAL,"雾蓝" to 0xFFE3E7EAL,"米白" to 0xFFF2EEE8L).forEach {(name,value)->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(value))
                .border(if(color==value) 2.dp else 1.dp,if(color==value) Ink.Primary else Ink.Divider,RoundedCornerShape(12.dp))
                .clickable(enabled=!saving) {color=value}.semantics {contentDescription="背景颜色：$name";selected=color==value}.padding(vertical=16.dp),
                contentAlignment=androidx.compose.ui.Alignment.Center) {AgendaText(name,size=12,color=Ink.Primary)}
        }
    }
    OutlinedTextField(text,{text=it;error=null},label={AgendaText("背景文字（最多两行）",size=12)},
        modifier=Modifier.fillMaxWidth().padding(top=16.dp).semantics {contentDescription="日程背景文字"},enabled=!saving,
        minLines=2,maxLines=3,shape=RoundedCornerShape(14.dp))
    error?.let {AgendaText(it,size=12,color=Ink.Danger,modifier=Modifier.padding(top=10.dp).semantics {liveRegion=LiveRegionMode.Polite})}
    Button({
        if(text.length>200 || text.lines().size>2) error="背景文字最多两行、200 字"
        else {val savedColor=color;val savedText=text;val image=useImage;val uri=selectedUri?.let(android.net.Uri::parse)
            saving=true;error=null;scope.launch {
                try {repository.saveAppearance(savedColor,savedText,image,uri);onClose()}
                catch(c:CancellationException) {throw c}
                catch(e:Exception) {error="背景未保存，请检查图片或可用空间；填写内容已保留"}
                finally {saving=false}
            }
        }
    },enabled=!saving,shape=RoundedCornerShape(11.dp),modifier=Modifier.padding(top=16.dp)) {AgendaText(if(saving) "正在保存…" else "保存背景与文字",color=Ink.Surface)}
}
