"""Prototipo del logo IV por capas (para validar geometría y tonos antes de pasarlo a la app)."""
import math, sys
from PIL import Image, ImageDraw
I = [(0,0),(188,0),(188,98),(144,99),(144,321),(188,322),(188,425),(0,425),(0,322),(44,321),(44,99),(0,98)]
c = 452
V = [(248,0),(c-36,0),(c-36,98),(c-67,99),(c-14,277),(c+14,277),(c+67,99),(c+36,98),(c+36,0),(c+204,0),(c+204,98),(c+166,99),(c+71,425),(c-71,425),(c-166,99),(248,98)]
def area(p): return sum(p[i][0]*p[(i+1)%len(p)][1]-p[(i+1)%len(p)][0]*p[i][1] for i in range(len(p)))/2
def normals(p):
    s = 1 if area(p) > 0 else -1  # y hacia abajo: área>0 = horario en pantalla
    out=[]
    for i in range(len(p)):
        (x0,y0),(x1,y1)=p[i],p[(i+1)%len(p)]
        dx,dy=x1-x0,y1-y0; L=math.hypot(dx,dy)
        out.append((s*dy/L, -s*dx/L))   # normal hacia afuera
    return out
def offset(p,d):
    n=normals(p); out=[]
    for i in range(len(p)):
        a,b=n[i-1],n[i]; mx,my=a[0]+b[0],a[1]+b[1]; k=1+a[0]*b[0]+a[1]*b[1]
        out.append((p[i][0]+d*mx/k, p[i][1]+d*my/k))
    return out
def tone(nx,ny):
    ang=math.degrees(math.atan2(-ny,nx))%360   # 0 = derecha, 90 = arriba
    stops=[(0,116),(90,192),(180,248),(270,184),(360,116)]
    for (a0,v0),(a1,v1) in zip(stops,stops[1:]):
        if a0<=ang<=a1: return int(v0+(v1-v0)*(ang-a0)/(a1-a0))
GRAD=[(0,0),(.25,0),(.28,5),(.31,10),(.34,15),(.37,22),(.395,32),(.42,47),(.45,63),(.48,59),(.51,49),(.54,43),(.565,36),(.59,29),(.62,23),(.65,17),(.68,14),(.71,11),(.73,8),(.76,6),(.79,3),(.85,0),(1,0)]
S=1; W,H=1000,560; ox,oy=60,60
im=Image.new('RGBA',(W,H),(60,60,70,255)); d=ImageDraw.Draw(im)
T=lambda p:[(ox+x*S,oy+y*S) for x,y in p]
for P in (I,V): d.polygon(T(offset(P,42)),fill=(0,0,0))
for P in (I,V):
    o4,o20,n=offset(P,4),offset(P,20),normals(P)
    for i in range(len(P)):
        j=(i+1)%len(P); g=tone(*n[i]); d.polygon(T([o4[i],o4[j],o20[j],o20[i]]),fill=(g,g,g))
for P in (I,V): d.polygon(T(offset(P,4)),fill=(250,250,252))
mask=Image.new('L',(W,H)); dm=ImageDraw.Draw(mask)
for P in (I,V): dm.polygon(T(P),fill=255)
grad=Image.new('RGB',(W,H))
for y in range(H):
    t=(y-oy)/425
    v=0
    for (a0,v0),(a1,v1) in zip(GRAD,GRAD[1:]):
        if a0<=t<=a1: v=v0+(v1-v0)*(t-a0)/(a1-a0)
    ImageDraw.Draw(grad).line([(0,y),(W,y)],fill=(int(v),int(v*0.93),int(v*0.94)))
im.paste(grad,mask=mask)
ref=Image.open(sys.argv[1]).convert('RGBA'); bg=Image.new('RGBA',ref.size,(60,60,70,255)); bg.alpha_composite(ref)
out=Image.new('RGB',(W+ref.width,max(H,ref.height))); out.paste(im,(0,0)); out.paste(bg,(W,0)); out.thumbnail((1400,1400)); out.save(sys.argv[2])
