from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

out=Path(__file__).resolve().parent/"watchface/src/main/res/drawable"
out.mkdir(parents=True,exist_ok=True)
S=3; Q=450; N=Q*S
MINT="#3FF4AE"; DARK="#142E26"; SOFT="#83988F"; WHITE="#FFFFFF"
img=Image.new("RGB",(N,N),"#000000")
p=ImageDraw.Draw(img)
def sc(x): return round(x*S)
def circ(cx,cy,r,color,width=1):
    p.ellipse((sc(cx-r),sc(cy-r),sc(cx+r),sc(cy+r)),outline=color,width=sc(width))
def arc(cx,cy,r,a,b,col,width):
    points=[]
    for k in range(max(20,int(abs(b-a)*2))+1):
        t=math.radians(a+(b-a)*k/max(20,int(abs(b-a)*2)))
        points.append((sc(cx+r*math.sin(t)),sc(cy-r*math.cos(t))))
    p.line(points,fill=col,width=sc(width),joint="curve")
def line(points,col,w=1):
    p.line([(sc(x),sc(y)) for x,y in points],fill=col,width=sc(w),joint="curve")
for i in range(60):
    a=2*math.pi*i/60
    r0=203 if i%5==0 else 208
    r1=213
    line([(225+r0*math.sin(a),225-r0*math.cos(a)),(225+r1*math.sin(a),225-r1*math.cos(a))], "#547065" if i%5==0 else "#253D33",2 if i%5==0 else 1)
for x,y in [(138,273),(312,273),(138,369),(312,369)]:
    circ(x,y,45,"#17342A",7)
    p.ellipse((sc(x-38),sc(y-38),sc(x+38),sc(y+38)),fill="#020806")
line([(225,220),(225,413)],"#102A23")
line([(185,322),(265,322)],"#102A23")
p.rounded_rectangle((sc(212),sc(102),sc(238),sc(105)),radius=sc(2),fill=MINT)
# Tiny line icons in each complication.
p.rounded_rectangle((sc(126),sc(245),sc(149),sc(258)),radius=sc(2),outline=MINT,width=sc(2))
p.rectangle((sc(150),sc(249),sc(153),sc(254)),fill=MINT)
p.rectangle((sc(129),sc(248),sc(142),sc(255)),fill=MINT)
p.polygon([(sc(x),sc(y)) for x,y in [(299,258),(306,253),(309,245),(316,252),(324,256),(325,262),(299,262)]],fill=MINT)
pts=[]
for k in range(101):
    t=2*math.pi*k/100
    pts.append((138+0.83*16*math.sin(t)**3,352-0.83*(13*math.cos(t)-5*math.cos(2*t)-2*math.cos(3*t)-math.cos(4*t))))
line(pts,MINT,2)
circ(323,350,7,MINT,1)
p.arc((sc(297),sc(348),sc(324),sc(364)),180,340,fill=WHITE,width=sc(2))
line([(300,363),(320,363)],WHITE,2)
dial=img.resize((450,450),Image.Resampling.LANCZOS)
dial.save(out/"dial.png", optimize=True)
prev=dial.copy()
d=ImageDraw.Draw(prev)
fontpath="/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
boldpath="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
def txt(value,center_x,y,size,color=WHITE,bold=False):
    f=ImageFont.truetype(boldpath if bold else fontpath,size)
    b=d.textbbox((0,0),value,font=f)
    d.text((center_x-(b[2]-b[0])/2,y-b[1]),value,font=f,fill=color)
def draw_arc(cx,cy,r,angle):
    pts=[]
    for j in range(121):
        t=math.radians(j*angle/120)
        pts.append((cx+r*math.sin(t),cy-r*math.cos(t)))
    d.line(pts,fill=MINT,width=6,joint="curve")
draw_arc(138,273,45,310)
draw_arc(312,273,45,280)
txt("ZO 15 NOV",225,75,19,"#D4EBE2")
txt("10:08",225,122,72,WHITE,True)
for x,y,v in [(138,265,"86%"),(312,265,"7842"),(138,362,"72"),(312,362,"18°")]:
    txt(v,x,y,20,WHITE,True)
for x,y,v in [(138,295,"BAT"),(312,295,"STEPS"),(138,390,"BPM"),(312,390,"WEER")]:
    txt(v,x,y,10,SOFT)
prev.save(out/"preview.png", optimize=True)
print("Generated 450x450 dial and preview PNGs")
