"""Campus Route Planner desktop host. Coordinates internally are WGS84 lon,lat."""
import json
import math
import os
from pathlib import Path
import sys

HOME = Path(os.environ.get('LOCALAPPDATA', Path.home())) / 'CampusRoutePlanner'
SOURCE = '© OpenStreetMap contributors | ODbL 1.0 | https://www.openstreetmap.org/copyright | 本机步行寻路'

def atomic(path, data):
    path = Path(path)
    tmp = path.with_suffix(path.suffix + '.tmp')
    tmp.write_text(data, encoding='utf-8')
    os.replace(tmp, path)

def distance(points):
    total = 0
    for (a,b),(c,d) in zip(points, points[1:]):
        r = math.pi / 180
        h = math.sin((d-b)*r/2)**2 + math.cos(b*r)*math.cos(d*r)*math.sin((c-a)*r/2)**2
        total += 6371000 * 2 * math.asin(math.sqrt(min(1,h)))
    return total

def validate(points):
    if not 2 <= len(points) <= 10000:
        raise ValueError('轨迹需有 2–10000 个坐标点')
    clean=[]
    for p in points:
        if len(p)!=2 or not all(isinstance(v,(float,int)) and math.isfinite(v) for v in p) or not -180<=p[0]<=180 or not -90<=p[1]<=90:
            raise ValueError('无效 WGS84 坐标')
        if not clean or distance([clean[-1],p]) > .01: clean.append(p)
    if len(clean)<2 or distance(clean)<.1: raise ValueError('路线有效长度不足 0.1 米')
    return points

def parse_csv(text):
    if len(text.encode('utf-8'))>2*1024*1024: raise ValueError('CSV 超过 2 MiB')
    points=[]
    for line in text.lstrip('\ufeff').splitlines():
        line=line.strip()
        if not line or line.startswith('#') or line.lower()=='latitude,longitude': continue
        row=line.split(',')
        if len(row)!=2: raise ValueError('每行必须为 latitude,longitude')
        lat,lon=map(float,row)
        points.append([lon,lat])
    return validate(points)

def csv_text(points):
    validate(points)
    text='latitude,longitude\n'+''.join(f'{lat:.8f},{lon:.8f}\n' for lon,lat in points)
    parse_csv(text)
    return text

def project_data(data):
    if not isinstance(data,dict) or data.get('version')!=1: raise ValueError('不支持的工程版本')
    anchors=data.get('anchors',[])
    if not isinstance(anchors,list) or len(anchors)>49: raise ValueError('工程地点无效或超限')
    for point in anchors:
        if not isinstance(point,list) or len(point)!=2 or not all(isinstance(v,(int,float)) and math.isfinite(v) for v in point) or not -180<=point[0]<=180 or not -90<=point[1]<=90: raise ValueError('工程地点坐标无效')
    points=data.get('points',[]); reference=data.get('reference',[])
    if points: validate(points)
    if reference: validate(reference)
    target=data.get('target',3)
    if not isinstance(target,(int,float)) or not math.isfinite(target) or target<0: raise ValueError('目标距离无效')
    return {'version':1,'name':str(data.get('name','我的校园路线'))[:200], 'anchors':anchors,'closed':bool(data.get('closed',True)), 'target':target,'points':points,'reference':reference,'source':str(data.get('source','用户轨迹')),'valid':bool(data.get('valid',False)) and bool(points),'anchorsConfirmed':bool(data.get('anchorsConfirmed',False))}

if __name__ == "__main__":
    try:
        from native_app import main
        main()
    except Exception:
        import traceback
        if len(sys.argv)>2 and sys.argv[1]=="--smoke-test":
            Path(sys.argv[2]).write_text(traceback.format_exc(),encoding="utf-8")
        raise
