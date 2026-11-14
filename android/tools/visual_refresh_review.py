"""Visual smoke checks on the dedicated Agenda_VisualReview emulator only.

Resets this emulator application to synthetic fixtures; never targets a real device.
Captures screenshots and assertions under android/review/visual-refresh.
"""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--adb',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1];out=root/'review/visual-refresh';out.mkdir(parents=True,exist_ok=True)
serial='emulator-5556';package='com.agenda.local';report=[]
def adb(*args,binary=False):
 r=subprocess.run([a.adb,'-s',serial,*args],capture_output=True,check=True,timeout=120)
 return r.stdout if binary else r.stdout.decode('utf-8',errors='replace')
def tree():
 for attempt in range(3):
  try:adb('shell','uiautomator','dump','/sdcard/agenda-daily.xml');break
  except subprocess.CalledProcessError:
   if attempt==2:raise
   time.sleep(.5)
 return ET.fromstring(adb('exec-out','cat','/sdcard/agenda-daily.xml'))
def nodes(label,attr='content-desc'):return [n for n in tree().iter('node') if n.get(attr)==label]
def click(node):
 x,y,r,b=map(int,re.findall(r'\d+',node.get('bounds')));assert r>x and b>y
 adb('shell','input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.4)
def tap(label,attr='content-desc'):
 for attempt in range(4):
  found=nodes(label,attr)
  if found:click(found[0]);return
  time.sleep(.4)
 raise AssertionError('Missing '+label)
def wait(label,attr='text'):
 for _ in range(5):
  if nodes(label,attr):return
  time.sleep(.3)
 raise AssertionError('Timed out '+label)
def replace(label,old,new):
 tap(label);adb('shell','input','keyevent','123')
 if old:adb('shell','input','keyevent',*(['67']*len(old)))
 if new:adb('shell','input','text',new)
def hide():adb('shell','input','keyevent','4');time.sleep(.3)
def capture(name):
 (out/f'{name}.xml').write_text(ET.tostring(tree(),encoding='unicode'),encoding='utf-8')
 (out/f'{name}.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
def check(name,passed):
 report.append(dict(check=name,passed=bool(passed)))
 (out/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');assert passed,name
def start():adb('shell','am','start','-W','-n',package+'/com.agenda.app.MainActivity');time.sleep(.7)
assert adb('shell','getprop','ro.boot.qemu.avd_name').strip()=='Agenda_VisualReview'
print(adb('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk')),flush=True)
adb('shell','pm','clear',package)
start()
tap('行动');capture('01-action-empty')
check('urgent-card-above-categories',int(re.findall(r'\d+',nodes('尽快做','text')[0].get('bounds'))[1]) < int(re.findall(r'\d+',nodes('行动卡片：随手做')[0].get('bounds'))[1]))
tap('日程');capture('02-schedule-plant')
tap('事项');tap('新建任务');tap('创建对象','text');replace('名称','','ReviewProject');hide();tap('创建','text');wait('ReviewProject')
tap('新建任务');replace('名称','','UrgentReview');hide();tap('归属、日期、意愿','text');tap('ReviewProject','text');tap('日期','text');tap('今天','text');tap('归属、日期、意愿','text');capture('03-create-glass');tap('创建','text')
tap('新建任务');replace('名称','','LaterReview');hide();tap('归属、日期、意愿','text');tap('ReviewProject','text');tap('日期','text');tap('明天','text');tap('归属、日期、意愿','text');tap('创建','text')
tap('行动');capture('04-action-urgent');check('only-most-urgent-task',bool(nodes('UrgentReview','text')) and not nodes('LaterReview','text'))
tap('完成：UrgentReview');wait('LaterReview');check('urgent-completion-promotes-next-task',not nodes('UrgentReview','text') and bool(nodes('LaterReview','text')))
tap('事项');tap('切换事项视图');capture('05-board-half-progress')
tap('事项卡片：ReviewProject');capture('06-board-expanded');tap('完成：LaterReview');capture('07-board-complete-progress')
# The expanded card can be collapsed without affecting completion state.
tap('切换事项视图');capture('08-graph-default')
tap('ReviewProject','text');capture('09-graph-selected');tap('ReviewProject','text');capture('10-graph-expanded')
tap('日程');adb('shell','settings','put','system','font_scale','1.3');time.sleep(1);capture('11-schedule-large-font')
tap('事项');tap('切换事项视图');capture('12-board-large-font')
adb('shell','wm','size','880x1760');time.sleep(1);tap('行动');capture('13-compact-action');check('compact-navigation-reachable',all(nodes(label) for label in ('事项','行动','日程')))
adb('shell','wm','size','reset');adb('shell','settings','put','system','font_scale','1.0')
tap('事项');tap('新建任务');replace('名称','','DiscardReview');hide();tap('关闭');check('unsaved-close-protected',bool(nodes('放弃编辑','text')));tap('放弃编辑','text')
check('discard-does-not-create-task',not nodes('DiscardReview','text'))
print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)

tap('事项');tap('切换事项视图');tap('UrgentReview','text');tap('关闭');capture('14-graph-task-selected')
check('selected-node-switches',any(n.get('checked')=='true' and any(c.get('text')=='UrgentReview' for c in n.iter('node')) for n in tree().iter('node')))
tap('日程');check('navigation-selection-updates',nodes('日程')[0].get('checked')=='true' and nodes('事项')[0].get('checked')=='false')
