"""Shared coordinate constants and eight-iteration inverse, matching Android MapCoordinates."""
import math
SYSTEMS={'WGS84','GCJ02','BD09'}
def forward(p):
 x0,y0=p
 if x0<72.004 or x0>137.8347 or y0<.8293 or y0>55.8271:return list(p)
 x,y=x0-105,y0-35;pi=math.pi;sin=math.sin
 a=-100+2*x+3*y+.2*y*y+.1*x*y+.2*math.sqrt(abs(x))
 a+=(20*sin(6*x*pi)+20*sin(2*x*pi))*2/3+(20*sin(y*pi)+40*sin(y*pi/3))*2/3+(160*sin(y*pi/12)+320*sin(y*pi/30))*2/3
 b=300+x+2*y+.1*x*x+.1*x*y+.1*math.sqrt(abs(x))
 b+=(20*sin(6*x*pi)+20*sin(2*x*pi))*2/3+(20*sin(x*pi)+40*sin(x*pi/3))*2/3+(150*sin(x*pi/12)+300*sin(x*pi/30))*2/3
 rad=y0*pi/180;magic=1-.00669342162296594323*sin(rad)**2;root=math.sqrt(magic)
 return [x0+b*180/(6378245/root*math.cos(rad)*pi),y0+a*180/((6378245*(1-.00669342162296594323))/(magic*root)*pi)]
def inverse(p):
 guess=list(p)
 for _ in range(8):
  v=forward(guess);guess=[guess[i]+p[i]-v[i] for i in (0,1)]
 return guess
def convert(p,source,target):
 if source not in SYSTEMS or target not in SYSTEMS:raise ValueError('未知坐标系')
 if source==target:return list(p)
 xp=math.pi*3000/180
 if source=='BD09':
  x,y=p[0]-.0065,p[1]-.006;z=math.hypot(x,y)-.00002*math.sin(y*xp);t=math.atan2(y,x)-.000003*math.cos(x*xp);p=[z*math.cos(t),z*math.sin(t)];source='GCJ02'
 w=inverse(p) if source=='GCJ02' else list(p)
 if target=='WGS84':return w
 g=forward(w)
 if target=='GCJ02':return g
 x,y=g;z=math.hypot(x,y)+.00002*math.sin(y*xp);t=math.atan2(y,x)+.000003*math.cos(x*xp);return [z*math.cos(t)+.0065,z*math.sin(t)+.006]
