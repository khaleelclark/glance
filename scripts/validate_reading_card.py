#!/usr/bin/env python3
"""Focused synthetic checks for the enlarged, range-filled production reading card."""
import json,re,subprocess
import validate_ui_overhaul as ui
from validate_refinement_026 import bounds,shown,scroll_to,norm
from validate_refinement_028 import PngPixels,pixels
GREEN=(139,220,159);RED=(255,155,155);YELLOW=(255,217,103)
INK=(21,21,26);GRAPHITE=(20,20,23)
def capture(name):
 data=subprocess.check_output(ui.ADB+['exec-out','screencap','-p'],timeout=30)
 (ui.EVIDENCE/f'api36-reading-card-0.29.0-{name}-demo.png').write_bytes(data)
 return PngPixels(data)
def normalized_node(text,nodes=None):
 return next(n for n in (nodes if nodes is not None else ui.nodes()) if any(norm(n.get(key,''))==norm(text) for key in ['text','content-desc']))
def scroll_reading_to(text):
 for _ in range(12):
  nodes=ui.nodes()
  if any(norm(text) in norm(ui.label(n)) for n in nodes):return
  columns=[n for n in nodes if n.get('scrollable')=='true']
  # The reading summary is the left pane in landscape; the chart has its own scroll.
  column=min(columns,key=lambda n:bounds(n)[0]);left,top,right,bottom=bounds(column)
  ui.adb('shell','input','swipe',str(left+8),str(top+(bottom-top)*3//4),str(left+8),str(top+(bottom-top)//4),'250');ui.settle()
 raise AssertionError('Reading content is not reachable: '+text)
def main():
 assert ui.SERIAL.startswith('emulator-') and ui.adb('shell','getprop','ro.kernel.qemu').strip()=='1'
 old={k:ui.adb('shell','settings','get','system',k).strip() for k in ['font_scale','user_rotation','accelerometer_rotation']}
 old_night=ui.adb('shell','cmd','uimode','night').strip();checks={};heights={}
 source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ui.ROOT,text=True).strip()
 dirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=ui.ROOT,text=True).strip())
 density=int(re.findall(r'\d+',ui.adb('shell','wm','density'))[-1])/160
 try:
  ui.adb('shell','settings','put','system','font_scale','1.0');ui.adb('shell','settings','put','system','accelerometer_rotation','0');ui.adb('shell','settings','put','system','user_rotation','0')
  ui.adb('shell','cmd','uimode','night','no');ui.adb('shell','input','keyevent','KEYCODE_WAKEUP');ui.adb('shell','wm','dismiss-keyguard');ui.adb('shell','cmd','statusbar','collapse')
  for mode,value,color in [('fresh','113',GREEN),('low','65',RED),('high','250',YELLOW),('sourceLow','LOW',RED),('sourceHigh','HIGH',YELLOW)]:
   ui.start(mode);nodes=ui.nodes();im=capture(mode);box=bounds(normalized_node(value,nodes))
   assert im.getpixel((box[0]-16,box[1]+16))==color
   assert pixels(im,box,INK)>50 and pixels(im,box,color)>100
   for label in ['Reading at 11:59 AM · 1 min ago','mg/dL','Trend Holding steady','Holding steady']:
    assert pixels(im,bounds(normalized_node(label,nodes)),INK)>5,label
   chip_top=bounds(next(n for n in nodes if 'Show latest 3 hours' in ui.label(n)))[1]
   colored=[y for y in range(chip_top) if im.getpixel((im.width//2,y))==color]
   height=colored[-1]-colored[0]+1
   assert height>=round(196*density)-2,(mode,height,density)
   heights[mode]=height/density
   checks[mode+'RangeBackgroundDarkContentAndTallerCard']=True
  for mode,label,time in [('stale','No recent reading','Reading at 11:40 AM · 20 min ago'),('delayed','Reading delayed','Reading at 11:53 AM · 7 min ago'),('invalidTime','Reading time unavailable','Reading at 12:10 PM')]:
   ui.start(mode);nodes=ui.nodes();im=capture(mode);box=bounds(normalized_node('113',nodes))
   assert im.getpixel((box[0]-16,box[1]+16))==GRAPHITE and any(norm(label) in norm(ui.label(n)) for n in nodes) and any(norm(time) in norm(ui.label(n)) for n in nodes)
   assert pixels(im,box,(240,238,244))>50
   checks[mode+'RetainsNeutralCardWarningAndSourceTime']=True
  ui.start('noReading');nodes=ui.nodes();im=capture('no-reading');assert any('Waiting for first reading' in ui.label(n) for n in nodes) and any('No reading yet' in ui.label(n) for n in nodes) and not any(n.get('text')=='113' for n in nodes)
  box=bounds(normalized_node('No reading yet',nodes));assert im.getpixel((box[0]-16,box[1]+16))==GRAPHITE
  checks['absentReadingRetainsNeutralWaitingCard']=True
  ui.adb('shell','cmd','uimode','night','yes');ui.start();im=capture('system-dark');box=bounds(ui.find('113',True));assert im.getpixel((box[0]-16,box[1]+16))==GREEN
  checks['bothSystemModesKeepSameRangeBackgroundAndContrast']=True
  ui.tap('Settings',True);scroll_to('Accent color');ui.tap('Accent color',True);scroll_to('Coral');ui.tap('Coral',True);ui.tap('Now',True);im=capture('coral-accent');box=bounds(ui.find('113',True));assert im.getpixel((box[0]-16,box[1]+16))==GREEN and pixels(im,box,INK)>50
  checks['accentChangesDoNotRecolorTheReadingRangeCard']=True
  ui.adb('shell','settings','put','system','font_scale','2.0')
  for rotation,name in [('0','portrait'),('1','landscape')]:
   ui.adb('shell','settings','put','system','user_rotation',rotation);ui.start('high')
   for label in ['Reading at 11:59 AM · 1 min ago','250','mg/dL','Trend Holding steady','Holding steady']:
    scroll_reading_to(label)
   capture('large-text-'+name)
   scroll_to('Show latest 3 hours');ui.tap('Show latest 3 hours',True);assert ui.checked_node('Show latest 3 hours').get('checked')=='true'
   capture('large-text-'+name+'-chart');ui.tap('Settings',True);assert ui.visible('Units')
   checks[name+'TwoHundredPercentKeepsCardContentChartAndSettingsReachable']=True
  apk=ui.adb('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
  report={'synthetic':True,'device':'API36 emulator','checks':checks,'allPassed':True,'freshCardHeightsDp':heights,'fixtureApkSha256':ui.adb('shell','sha256sum',apk).split()[0],'sourceCommitAtRun':source,'sourceDirtyAtRun':dirty,'fixtureProductionCommit':'8ffd2ac','scope':'Production ReadingHero/FlowRow and scrolling; synthetic source states, actual screenshot colors and touch; requires visual clipping inspection','SamsungOneUI':'not tested','liveAccount':'not tested','TalkBack':'not tested'}
  (ui.EVIDENCE/'api36-reading-card-0.29.0.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
 finally:
  for key,value in old.items():ui.adb('shell','settings','put','system',key,value)
  ui.adb('shell','cmd','uimode','night','yes' if 'yes' in old_night else 'no')
if __name__=='__main__':main()
