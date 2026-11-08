"""Local OSM pedestrian graph and Dijkstra. No hosted routing API or credentials."""
import heapq
import json
import math
import threading
import time
import urllib.request
import xml.etree.ElementTree as ET
from main import HOME, SOURCE, atomic, distance, validate

UA='CampusRoutePlanner/2.0 (personal native Windows application)'
ALLOWED={'footway','pedestrian','path','steps','residential','service','unclassified','living_street','track','road','tertiary','tertiary_link','secondary','secondary_link','primary','primary_link'}

def walkable(tags):
    foot=tags.get('foot',tags.get('access','yes'))
    return tags.get('highway') in ALLOWED and foot not in {'no','private','use_sidepath'} and tags.get('area')!='yes' and 'access:conditional' not in tags and 'foot:conditional' not in tags

def project(p,a,b):
    scale=math.cos(math.radians(p[1])); vx=(b[0]-a[0])*scale; vy=b[1]-a[1]
    t=max(0,min(1,(((p[0]-a[0])*scale)*vx+(p[1]-a[1])*vy)/(vx*vx+vy*vy))) if vx or vy else 0
    q=[a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])]
    return distance([p,q]),t,q

class Graph:
    def __init__(self,raw):
        root=ET.fromstring(raw); self.nodes={}; blocked=set(); self.places=[]; self.edges=[]
        for node in root.findall('node'):
            ident=int(node.get('id')); point=[float(node.get('lon')),float(node.get('lat'))];self.nodes[ident]=point
            tags={t.get('k'):t.get('v') for t in node.findall('tag')}
            access=tags.get('foot',tags.get('access','yes'))
            if access in ('no','private') or (tags.get('barrier') in ('wall','fence','block','hedge') and tags.get('foot') not in ('yes','designated','permissive')): blocked.add(ident)
            if tags.get('name'):self.places.append((tags['name'],point))
        for way in root.findall('way'):
            tags={t.get('k'):t.get('v') for t in way.findall('tag')};ids=[int(n.get('ref')) for n in way.findall('nd')]
            if tags.get('name'):
                known=[self.nodes[n] for n in ids if n in self.nodes]
                if known:self.places.append((tags['name'],known[len(known)//2]))
            if not walkable(tags):continue
            direction=tags.get('oneway:foot','no')
            for a,b in zip(ids,ids[1:]):
                if a in self.nodes and b in self.nodes and a not in blocked and b not in blocked and a!=b:
                    self.edges.append((a,b,direction))
        if not self.edges:raise ValueError('当前区域没有可用步行道路')

    def route(self,anchors,closed=True):
        if not 2<=len(anchors)<=49:raise ValueError('请选择 2–49 个地点')
        validate(anchors)
        nodes=dict(self.nodes); splits={}; snapped=[]; targets=[]
        for i,p in enumerate(anchors):
            candidates=[]
            for edge,(a,b,_) in enumerate(self.edges):
                gap,t,q=project(p,nodes[a],nodes[b]);candidates.append((gap,edge,t,q))
            gap,edge,t,q=min(candidates,key=lambda item:item[0])
            if gap>100:raise ValueError(f'第 {i+1} 个地点距已收录道路超过 100 米，请移到道路附近')
            a,b,_=self.edges[edge]
            if t<1e-8:ident=a
            elif t>1-1e-8:ident=b
            else:
                ident=-(i+1);nodes[ident]=q;splits.setdefault(edge,[]).append((t,ident))
            targets.append(ident);snapped.append(q)
        graph={}
        def connect(a,b):graph.setdefault(a,[]).append((b,distance([nodes[a],nodes[b]])))
        for e,(a,b,direction) in enumerate(self.edges):
            chain=[a]+[n for _,n in sorted(splits.get(e,[]))]+[b]
            for u,v in zip(chain,chain[1:]):
                if direction!='-1':connect(u,v)
                if direction not in ('yes','1','true'):connect(v,u)
        if closed:targets.append(targets[0])
        result=[]
        for leg,(start,end) in enumerate(zip(targets,targets[1:])):
            queue=[(0,start)];cost={start:0};parent={}
            while queue:
                value,node=heapq.heappop(queue)
                if value!=cost[node]:continue
                if node==end:break
                for next_node,weight in graph.get(node,[]):
                    new=value+weight
                    if new<cost.get(next_node,float('inf')):
                        cost[next_node]=new;parent[next_node]=node;heapq.heappush(queue,(new,next_node))
            if end not in cost:raise ValueError(f'第 {leg+1} 段道路不连通或禁止步行，请调整地点（不会用直线补齐）')
            path=[end]
            while path[-1]!=start:path.append(parent[path[-1]])
            points=[nodes[n] for n in reversed(path)]
            result.extend(points if not result else points[1:])
        validate(result)
        return {'points':result,'snapped':snapped,'meters':distance(result),'source':SOURCE}

class RoadStore:
    def __init__(self):self.lock=threading.Lock();self.box=None;self.graph=None
    def load(self,points,refresh=False):
        with self.lock:
            if not refresh and self.box and all(self.box[0]<=p[0]<=self.box[2] and self.box[1]<=p[1]<=self.box[3] for p in points):return self.graph
            west=min(p[0] for p in points)-.008;south=min(p[1] for p in points)-.008
            east=max(p[0] for p in points)+.008;north=max(p[1] for p in points)+.008
            if east-west>.06 or north-south>.06:raise ValueError('第一版用于校园范围，请将地点控制在约 5 公里范围内')
            box=[round(west,4),round(south,4),round(east,4),round(north,4)]
            folder=HOME/'roads';folder.mkdir(parents=True,exist_ok=True)
            path=folder/('_'.join(map(str,box))+'.osm')
            if not refresh and path.exists() and time.time()-path.stat().st_mtime<7*86400:raw=path.read_bytes()
            else:
                req=urllib.request.Request('https://api.openstreetmap.org/api/0.6/map?bbox='+','.join(map(str,box)),headers={'User-Agent':UA})
                try:
                    with urllib.request.urlopen(req,timeout=45) as response:raw=response.read(16*1024*1024)
                except OSError as exc:raise ValueError('道路数据加载失败，请检查网络后重试；已有 CSV 仍可打开') from exc
                Graph(raw) # Validate before replacing cached road data.
                tmp=path.with_suffix('.tmp');tmp.write_bytes(raw);tmp.replace(path)
            self.graph=Graph(raw);self.box=box
            return self.graph
