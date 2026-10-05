#!/usr/bin/env python3
"""Production Compose screens with synthetic data; pixel, selection and persistence checks."""
import json,re,struct,subprocess,zlib
import validate_ui_overhaul as ui
from validate_refinement_026 import scroll_to,shown,bounds,text_node,norm

PRESETS={'Lilac':'B9AEF5','Teal':'79D3C5','Ocean':'8AB9F7','Berry':'D4ACF5','Forest':'A5D6A7','Coral':'FFA8B7','Sunset':'FFC28A'}
GREEN=(139,220,159); RED=(255,155,155); YELLOW=(255,217,103)
def capture(name):
    data=subprocess.run(ui.ADB+['exec-out','screencap','-p'],check=True,capture_output=True,timeout=30).stdout
    (ui.EVIDENCE/f'api36-ui-0.28.0-{name}-demo.png').write_bytes(data)
    return PngPixels(data)

class PngPixels:
    """Read the emulator's 8-bit noninterlaced PNG without an optional imaging dependency."""
    def __init__(self,data):
        assert data[:8]==b'\x89PNG\r\n\x1a\n'
        at=8;compressed=[]
        while at<len(data):
            size=struct.unpack('>I',data[at:at+4])[0];kind=data[at+4:at+8];chunk=data[at+8:at+8+size];at+=size+12
            if kind==b'IHDR':
                self.width,self.height,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',chunk)
                assert depth==8 and color in [2,6] and interlace==0
                self.stride=3 if color==2 else 4
            if kind==b'IDAT':compressed.append(chunk)
        raw=zlib.decompress(b''.join(compressed));row_size=self.width*self.stride;self.rows=[];previous=bytes(row_size)
        for y in range(self.height):
            offset=y*(row_size+1);kind=raw[offset];row=bytearray(raw[offset+1:offset+1+row_size])
            assert 0<=kind<=4
            if kind:
                for x in range(row_size):
                    a=row[x-self.stride] if x>=self.stride else 0;b=previous[x];c=previous[x-self.stride] if x>=self.stride else 0
                    if kind==1:predict=a
                    elif kind==2:predict=b
                    elif kind==3:predict=(a+b)//2
                    else:
                        p=a+b-c;pa=abs(p-a);pb=abs(p-b);pc=abs(p-c)
                        predict=a if pa<=pb and pa<=pc else b if pb<=pc else c
                    row[x]=(row[x]+predict)&255
            previous=bytes(row);self.rows.append(previous)
    def getpixel(self,xy):
        x,y=xy;return tuple(self.rows[y][x*self.stride:x*self.stride+3])

def pixels(im,box,color):
    left,top,right,bottom=box;needle=bytes(color)+(b'\xff' if im.stride==4 else b'')
    return sum(row[left*im.stride:right*im.stride].count(needle) for row in im.rows[top:bottom])
def find_normalized(text):
    return next(n for n in ui.nodes() if any(norm(n.get(key,''))==norm(text) for key in ['text','content-desc']))

def verify_main(im,value,color,accent):
    value_box=bounds(ui.find(value,True)); assert pixels(im,value_box,color)>40
    assert im.getpixel((value_box[0]-12,value_box[1]+12))==(20,20,23),'graphite reading card'
    for name in ['Reading at 11:59 AM · 1 min ago','mg/dL','Trend Holding steady']:
        assert pixels(im,bounds(find_normalized(name)),color)==0,name
    label=bounds(ui.find('GLANCE',True));person=bounds(ui.find('DEMO · Alex',True))
    assert pixels(im,(0,label[1],label[0],person[3]),accent)>40,'droplet accent'
    assert im.getpixel((0,im.height//2))==(0,0,0),'black background'

def main():
    assert ui.SERIAL.startswith('emulator-') and ui.adb('shell','getprop','ro.kernel.qemu').strip()=='1'
    old={k:ui.adb('shell','settings','get','system',k).strip() for k in ['font_scale','user_rotation','accelerometer_rotation']}
    old_night=ui.adb('shell','cmd','uimode','night').strip();checks={}
    try:
        ui.adb('shell','settings','put','system','font_scale','1.0');ui.adb('shell','settings','put','system','accelerometer_rotation','0');ui.adb('shell','settings','put','system','user_rotation','0')
        ui.adb('shell','cmd','uimode','night','no');ui.adb('shell','input','keyevent','KEYCODE_WAKEUP');ui.adb('shell','wm','dismiss-keyguard');ui.adb('shell','cmd','statusbar','collapse')
        for mode,value,color in [('fresh','113',GREEN),('low','65',RED),('high','250',YELLOW)]:
            ui.start(mode);im=capture(mode);verify_main(im,value,color,(185,174,245));checks[mode+'OnlyGlucoseNumberHasRangeColor']=True
        ui.start('stale');im=capture('stale');assert ui.visible('No recent reading') and shown('Reading at 11:40 AM · 20 min ago')
        assert not any(pixels(im,bounds(ui.find('113',True)),c) for c in [GREEN,RED,YELLOW]);checks['staleValueIsNeutralAndWarningTimeRemain']=True
        ui.start();ui.tap('Settings',True);scroll_to('Accent color');ui.tap('Accent color',True)
        assert ui.checked_node('Lilac').get('checked')=='true';assert not ui.visible('Custom color') and not ui.visible('Hex color')
        capture('accent-presets');ui.adb('shell','input','keyevent','KEYCODE_BACK');ui.settle()
        for index,(name,hexcode) in enumerate(PRESETS.items(),1):
            ui.tap('Accent color',True);scroll_to(name);ui.tap(name,True)
            assert ui.events().get('accent')==index and ui.visible(name)
            ui.tap('Now',True);im=capture('accent-'+name.lower());verify_main(im,'113',GREEN,tuple(bytes.fromhex(hexcode)))
            ui.tap('Settings',True);scroll_to('Accent color')
        checks['allSevenPresetsDispatchOnceAndRecolorDropletWithoutChangingGlucoseColor']=True
        ui.start('accentStored');im=capture('accent-persisted');verify_main(im,'113',GREEN,tuple(bytes.fromhex(PRESETS['Sunset'])))
        checks['syntheticSavedAccentSurvivesActualActivityProcessRestart']=True
        ui.start('accentStorage');receipt=json.loads(ui.adb('shell','run-as','com.khaleel.glance','cat','files/accent-storage-check.json'))
        assert len(receipt)==3 and all(receipt.values());checks.update(receipt)
        ui.adb('shell','cmd','uimode','night','yes');ui.start();im=capture('system-dark');verify_main(im,'113',GREEN,(185,174,245));checks['blackPresentationWorksInBothSystemModes']=True
        ui.adb('shell','settings','put','system','font_scale','2.0')
        for rotation,name in [('0','portrait'),('1','landscape')]:
            ui.adb('shell','settings','put','system','user_rotation',rotation);ui.start('high');assert ui.visible('250') and ui.visible('mg/dL') and shown('Reading at 11:59 AM · 1 min ago')
            capture('large-text-'+name+'-reading')
            scroll_to('Show latest 3 hours');ui.tap('Show latest 3 hours',True);assert ui.checked_node('Show latest 3 hours').get('checked')=='true'
            capture('large-text-'+name+'-chart')
            ui.tap('Settings',True);scroll_to('Accent color');ui.tap('Accent color',True);scroll_to('Sunset');ui.tap('Sunset',True);assert ui.events().get('accent')==1
            checks[name+'AtTwoHundredPercentKeepsLargerValueTimeUnitChartAndPresetReachable']=True
        apk=ui.adb('shell','pm','path','com.khaleel.glance').strip().removeprefix('package:')
        assert re.fullmatch(r'/data/app/[A-Za-z0-9/_.=+~\-]+/base\.apk',apk)
        report={'synthetic':True,'device':'API 36 emulator','checks':checks,'allPassed':True,'fixtureApkSha256':ui.adb('shell','sha256sum',apk).split()[0],
            'sourceCommitAtRun':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'sourceDirtyAtRun':bool(subprocess.check_output(['git','status','--porcelain'],text=True).strip()),
            'scope':'Production GlanceRoot; native screenshot pixels, actual touch callbacks and isolated synthetic preferences; screenshots need visual inspection for clipping','liveAccount':'not tested','SamsungOneUI':'not tested','TalkBack':'not tested'}
        (ui.EVIDENCE/'api36-ui-0.28.0.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(checks))
    finally:
        for key,val in old.items():ui.adb('shell','settings','put','system',key,val)
        ui.adb('shell','cmd','uimode','night','yes' if 'yes' in old_night else 'no')
if __name__=='__main__':main()
