"""Native Tk desktop UI. No HTML, browser, JavaScript bridge or WebView."""
import concurrent.futures
import io
import json
import math
from pathlib import Path
import queue
import re
import sys
import threading
import time
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import urllib.parse
import urllib.request
from PIL import Image, ImageTk
from main import HOME, SOURCE, atomic, csv_text, distance, parse_csv, project_data
from routing import RoadStore, UA

def fresh():return dict(version=1,name='我的校园路线',anchors=[],closed=True,target=3,points=[],reference=[],source=SOURCE,valid=False)
def copy(value):return json.loads(json.dumps(value))
def pixel(p,z):
    lon,lat=p;lat=max(-85.0511,min(85.0511,lat));n=256*2**z
    return ((lon+180)/360*n,(1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n)
def coordinate(x,y,z):
    n=256*2**z;return [x/n*360-180,math.degrees(math.atan(math.sinh(math.pi*(1-2*y/n))))]

class App:
    def __init__(self,root):
        self.root=root;root.title('校园路线规划器 · 原生免密钥版');root.geometry('1280x820');root.minsize(950,650)
        HOME.mkdir(parents=True,exist_ok=True);self.store=RoadStore();self.state=fresh();self.dirty=False;self.history=[];self.future=[];self.revision=0
        self.center=[114.328,30.511];self.zoom=16;self.smoke='--smoke-test' in sys.argv;self.search_url='https://nominatim.openstreetmap.org/search'
        try:
            settings=json.loads((HOME/'native-settings.json').read_text('utf-8'));self.center=settings['center'];self.zoom=settings['zoom'];self.search_url=settings.get('search_url',self.search_url)
        except (OSError,ValueError,KeyError):pass
        self.pool=concurrent.futures.ThreadPoolExecutor(max_workers=4);self.events=queue.Queue();self.images={};self.pending=set();self.wanted=set();self.closed=False;self.search_id=0;self.search_lock=threading.Lock();self.last_search=0;self.search_cache={}
        style=ttk.Style();style.theme_use('clam');style.configure('TFrame',background='#f4f7fa');style.configure('TLabel',background='#f4f7fa',foreground='#18384b');style.configure('TButton',padding=7);style.configure('Title.TLabel',font=('Microsoft YaHei UI',19,'bold'))
        side=ttk.Frame(root,padding=18,width=340);side.pack(side='left',fill='y');side.pack_propagate(False)
        ttk.Label(side,text='校园路线规划器',style='Title.TLabel').pack(anchor='w')
        ttk.Label(side,text='本机算路 · 无需密钥 · 原生桌面窗口').pack(anchor='w',pady=(5,15))
        self.search_text=tk.StringVar();entry=ttk.Entry(side,textvariable=self.search_text);entry.pack(fill='x');entry.bind('<Return>',lambda e:self.search())
        ttk.Button(side,text='搜索地点 / 经纬度定位',command=self.search).pack(fill='x',pady=4)
        self.results=tk.Listbox(side,height=3,exportselection=False);self.results.pack(fill='x');self.results.bind('<<ListboxSelect>>',self.locate_result);self.found=[]
        self.name=tk.StringVar(value=self.state['name']);ttk.Entry(side,textvariable=self.name).pack(fill='x',pady=(12,5));self.name.trace_add('write',self.metadata)
        self.loop=tk.BooleanVar(value=True);ttk.Checkbutton(side,text='沿道路返回起点',variable=self.loop,command=self.toggle).pack(anchor='w')
        targetrow=ttk.Frame(side);targetrow.pack(fill='x',pady=6);ttk.Label(targetrow,text='目标公里数').pack(side='left');self.target=tk.StringVar(value='3');e=ttk.Entry(targetrow,textvariable=self.target,width=9);e.pack(side='right');e.bind('<FocusOut>',self.metadata);e.bind('<Return>',self.metadata)
        self.metrics=ttk.Label(side,text='0.00 km',font=('Segoe UI',23,'bold'));self.metrics.pack(anchor='w');self.delta=ttk.Label(side,text='');self.delta.pack(anchor='w')
        self.status=tk.StringVar(value='点击地图选点；拖动空白处移动地图，滚轮缩放。');ttk.Label(side,textvariable=self.status,wraplength=300).pack(fill='x',pady=8)
        row=ttk.Frame(side);row.pack(fill='x');
        for text,fn in [('撤销',self.undo),('重做',self.redo),('重算',self.changed)]:ttk.Button(row,text=text,command=fn).pack(side='left',expand=True,fill='x')
        self.list=tk.Listbox(side,height=7,exportselection=False);self.list.pack(fill='both',expand=True,pady=6)
        row=ttk.Frame(side);row.pack(fill='x')
        for text,fn in [('上移',lambda:self.move(-1)),('下移',lambda:self.move(1)),('删除',self.delete)]:ttk.Button(row,text=text,command=fn).pack(side='left',expand=True,fill='x')
        row=ttk.Frame(side);row.pack(fill='x',pady=(10,3))
        for text,fn in [('打开',self.open),('保存',self.save),('新建',self.new)]:ttk.Button(row,text=text,command=fn).pack(side='left',expand=True,fill='x')
        ttk.Button(side,text='导出手机 CSV',command=self.export).pack(fill='x',pady=4)
        ttk.Label(side,text='拖动编号点调整；点击或拖动路线增加途经点。\n数据 © OpenStreetMap contributors · ODbL',wraplength=300).pack(anchor='w',pady=6)
        self.canvas=tk.Canvas(root,bg='#e5ecef',highlightthickness=0);self.canvas.pack(side='right',fill='both',expand=True)
        self.canvas.bind('<Configure>',lambda e:self.render());self.canvas.bind('<ButtonPress-1>',self.press);self.canvas.bind('<B1-Motion>',self.drag);self.canvas.bind('<ButtonRelease-1>',self.release);self.canvas.bind('<MouseWheel>',self.wheel)
        root.protocol('WM_DELETE_WINDOW',self.close);root.after(100,self.poll)

    def submit(self,fn,done):
        def run():
            try:result=fn();self.events.put((done,result,None))
            except Exception as e:self.events.put((done,None,str(e)))
        self.pool.submit(run)
    def poll(self):
        if self.closed:return
        for _ in range(100):
            try:fn,result,error=self.events.get_nowait()
            except queue.Empty:break
            fn(result,error)
        self.root.after(70,self.poll)
    def checkpoint(self):self.history.append(copy(self.state));self.history=self.history[-60:];self.future=[];self.dirty=True
    def metadata(self,*args):
        if getattr(self,'syncing',False):return
        try:value=float(self.target.get())
        except ValueError:return
        if not math.isfinite(value) or value<0:return
        self.state['name']=self.name.get();self.state['target']=value;self.dirty=True;self.update_metrics()
    def update_metrics(self):
        meters=distance(self.state['points']);self.metrics.config(text=f'{meters/1000:.2f} km');diff=meters-self.state['target']*1000
        self.delta.config(text=f'{"超出" if diff>=0 else "还差"} {abs(diff):.0f} 米'+('' if self.state['valid'] else ' · 修改未规划'))
    def sync(self):
        self.syncing=True;self.name.set(self.state['name']);self.target.set(str(self.state['target']));self.loop.set(self.state['closed']);self.syncing=False;self.render()
    def toggle(self):self.checkpoint();self.state['closed']=self.loop.get();self.changed()
    def changed(self):
        self.revision+=1;version=self.revision;self.state['valid']=False;self.dirty=True;self.render()
        points=copy(self.state['anchors']);closed=self.state['closed']
        if len(points)<2:self.status.set('请至少选择两个地点。');return
        self.status.set('正在加载道路数据并在本机规划…')
        def work():return self.store.load(points).route(points,closed)
        def done(result,error):
            if version!=self.revision:return
            if error:self.status.set(error+'；当前修改不能导出。');return
            if self.state['reference'] and not self.state.get('anchorsConfirmed'):
                if not messagebox.askyesno('替换导入轨迹','是否使用新路线？原轨迹将保留为灰色参照。',parent=self.root):return
                self.state['anchorsConfirmed']=True
            self.state.update(points=result['points'],source=result['source'],valid=True)
            self.status.set('规划成功。地点已吸附到附近道路；请核对实际通行情况。');self.render()
        self.submit(work,done)
    def undo(self):
        if not self.history:return
        self.future.append(copy(self.state));self.state=self.history.pop();self.restored()
    def redo(self):
        if not self.future:return
        self.history.append(copy(self.state));self.state=self.future.pop();self.restored()
    def restored(self):
        self.revision+=1;self.dirty=True;self.sync()
        if not self.state['valid'] and len(self.state['anchors'])>1:self.changed()
    def move(self,direction):
        selection=self.list.curselection()
        if not selection:return
        i=selection[0];j=i+direction
        if not 0<=j<len(self.state['anchors']):return
        self.checkpoint();a=self.state['anchors'];a[i],a[j]=a[j],a[i];self.changed();self.list.selection_set(j)
    def delete(self):
        s=self.list.curselection()
        if s:self.checkpoint();self.state['anchors'].pop(s[0]);self.changed()

    def screen(self,p):
        x,y=pixel(p,self.zoom);cx,cy=pixel(self.center,self.zoom);return x-cx+self.canvas.winfo_width()/2,y-cy+self.canvas.winfo_height()/2
    def point(self,x,y):
        cx,cy=pixel(self.center,self.zoom);return coordinate(cx+x-self.canvas.winfo_width()/2,cy+y-self.canvas.winfo_height()/2,self.zoom)
    def render(self):
        if self.closed:return
        c=self.canvas;c.delete('all');w,h=c.winfo_width(),c.winfo_height();cx,cy=pixel(self.center,self.zoom);left,top=cx-w/2,cy-h/2
        self.wanted=set()
        for x in range(math.floor(left/256),math.floor((left+w)/256)+1):
            for y in range(math.floor(top/256),math.floor((top+h)/256)+1):
                key=(self.zoom,x,y)
                if not 0<=x<2**self.zoom or not 0<=y<2**self.zoom:continue
                self.wanted.add(key)
                if key in self.images:c.create_image(x*256-left,y*256-top,image=self.images[key],anchor='nw',tags='tile')
                elif key not in self.pending and not self.smoke:
                    self.pending.add(key);self.submit(lambda k=key:self.tile(k),lambda data,error,k=key:self.tile_done(k,data,error))
        self.overlays();self.list.delete(0,'end')
        for i,p in enumerate(self.state['anchors']):self.list.insert('end',f'{i+1}. {p[1]:.5f}, {p[0]:.5f}')
        self.update_metrics()
    def overlays(self):
        c=self.canvas;c.delete('overlay')
        for points,color,dash in [(self.state['reference'],'#87939d',(5,5)),(self.state['points'],'#167b80' if self.state['valid'] else '#a88d3c',None)]:
            if len(points)>1:
                coords=[v for p in points for v in self.screen(p)];kw={'fill':color,'width':5,'tags':'overlay'}
                if dash:kw['dash']=dash
                c.create_line(*coords,**kw)
        for i,p in enumerate(self.state['anchors']):
            x,y=self.screen(p);c.create_oval(x-13,y-13,x+13,y+13,fill='#167b80',outline='white',width=2,tags='overlay');c.create_text(x,y,text=str(i+1),fill='white',tags='overlay')
        c.create_rectangle(7,7,40,65,fill='white',outline='#aabac4',tags='overlay');c.create_text(24,21,text='+',font=('Segoe UI',18),tags='overlay');c.create_text(24,49,text='−',font=('Segoe UI',18),tags='overlay')
        w,h=c.winfo_width(),c.winfo_height();c.create_rectangle(max(0,w-330),h-24,w,h,fill='white',outline='',tags='overlay');c.create_text(w-8,h-12,text='© OpenStreetMap contributors · 本机步行寻路',anchor='e',tags='overlay')
    def tile(self,key):
        if key not in self.wanted:return None
        z,x,y=key;folder=HOME/'tiles';folder.mkdir(exist_ok=True);p=folder/f'{z}-{x}-{y}.png';meta=p.with_suffix('.json')
        try:
            if p.exists() and time.time()<json.loads(meta.read_text())['expiry']:return p.read_bytes()
        except (OSError,ValueError,KeyError):pass
        req=urllib.request.Request(f'https://tile.openstreetmap.org/{z}/{x}/{y}.png',headers={'User-Agent':UA})
        with urllib.request.urlopen(req,timeout=12) as r:
            raw=r.read();control=r.headers.get('Cache-Control','');age=re.search(r'max-age=(\d+)',control)
            if 'no-store' not in control:p.write_bytes(raw);atomic(meta,json.dumps({'expiry':time.time()+(int(age[1]) if age else 604800)}))
        return raw
    def tile_done(self,key,data,error):
        self.pending.discard(key)
        if data:
            self.images[key]=ImageTk.PhotoImage(Image.open(io.BytesIO(data)))
            if len(self.images)>200:
                for old in list(self.images):
                    if old not in self.wanted:del self.images[old]
                    if len(self.images)<=120:break
            if key in self.wanted:
                z,x,y=key;cx,cy=pixel(self.center,self.zoom);self.canvas.create_image(x*256-cx+self.canvas.winfo_width()/2,y*256-cy+self.canvas.winfo_height()/2,image=self.images[key],anchor='nw',tags='tile');self.canvas.tag_lower('tile');self.overlays()
        elif error:self.status.set('部分底图加载失败，可稍后移动地图重试；已下载路网仍可算路。')
    def press(self,e):
        if 7<=e.x<=40 and 7<=e.y<=65:self.zoom_by(1 if e.y<36 else -1);self.gesture=None;return
        self.gesture={'x':e.x,'y':e.y,'center':list(self.center),'marker':None,'line':False,'moved':False}
        for i,p in enumerate(self.state['anchors']):
            x,y=self.screen(p)
            if math.hypot(e.x-x,e.y-y)<16:self.gesture['marker']=i;return
        for a,b in zip(self.state['points'],self.state['points'][1:]):
            ax,ay=self.screen(a);bx,by=self.screen(b);vx,vy=bx-ax,by-ay;t=max(0,min(1,((e.x-ax)*vx+(e.y-ay)*vy)/(vx*vx+vy*vy))) if vx or vy else 0
            if math.hypot(e.x-ax-t*vx,e.y-ay-t*vy)<8:self.gesture['line']=True;break
    def drag(self,e):
        g=getattr(self,'gesture',None)
        if not g:return
        dx,dy=e.x-g['x'],e.y-g['y'];g['moved']=g['moved'] or math.hypot(dx,dy)>4
        if g['marker'] is None and not g['line']:
            cx,cy=pixel(g['center'],self.zoom);self.center=coordinate(cx-dx,cy-dy,self.zoom);self.render()
        else:
            self.canvas.delete('preview');self.canvas.create_oval(e.x-10,e.y-10,e.x+10,e.y+10,outline='#167b80',width=3,tags='preview')
    def release(self,e):
        g=getattr(self,'gesture',None);self.gesture=None;self.canvas.delete('preview')
        if not g:return
        if g['marker'] is not None:
            if g['moved']:self.checkpoint();self.state['anchors'][g['marker']]=self.point(e.x,e.y);self.changed()
        elif g['line'] or not g['moved']:
            if len(self.state['anchors'])>=49:self.status.set('最多 49 个地点');return
            self.checkpoint();p=self.point(e.x,e.y);index=len(self.state['anchors'])
            if g['line'] and len(self.state['anchors'])>1:
                from routing import project
                a=self.state['anchors'];indices=range(len(a) if self.state['closed'] else len(a)-1);index=min(indices,key=lambda i:project(p,a[i],a[(i+1)%len(a)])[0])+1
            self.state['anchors'].insert(index,p);self.changed()
    def wheel(self,e):self.zoom_by(1 if e.delta>0 else -1)
    def zoom_by(self,n):self.zoom=max(3,min(19,self.zoom+n));self.render()

    def search(self):
        text=self.search_text.get().strip()
        if not text:return
        try:
            lon,lat=map(float,text.replace('，',',').split(','))
            if -180<=lon<=180 and -85<=lat<=85:self.center=[lon,lat];self.zoom=16;self.render();return
        except ValueError:pass
        self.search_id+=1;version=self.search_id;self.status.set('搜索中…')
        def work():
            if self.store.graph:
                matches=[{'name':name,'point':p} for name,p in self.store.graph.places if text.lower() in name.lower()]
                if matches:return matches[:8]
            with self.search_lock:
                if text in self.search_cache:return self.search_cache[text]
                time.sleep(max(0,1.1-(time.monotonic()-self.last_search)));self.last_search=time.monotonic()
                q=urllib.parse.urlencode({'q':text,'format':'jsonv2','limit':8,'accept-language':'zh-CN'})
                req=urllib.request.Request(self.search_url+'?'+q,headers={'User-Agent':UA})
                with urllib.request.urlopen(req,timeout=15) as r:data=json.load(r)
                result=[{'name':v['display_name'],'point':[float(v['lon']),float(v['lat'])]} for v in data];self.search_cache[text]=result;return result
        def done(result,error):
            if version!=self.search_id:return
            if error:self.status.set('搜索失败，可直接地图选点或输入 经度,纬度 定位。');return
            self.found=result;self.results.delete(0,'end')
            for item in result:self.results.insert('end',item['name'])
            self.status.set('选择结果定位，再点击地图添加地点。' if result else '未找到地点，请直接地图选点。')
        self.submit(work,done)
    def locate_result(self,e):
        s=self.results.curselection()
        if s:self.center=self.found[s[0]]['point'];self.zoom=16;self.render()
    def discard(self):return not self.dirty or messagebox.askyesno('未保存修改','当前工程尚未保存，是否放弃修改？',parent=self.root)
    def new(self):
        if self.discard():self.revision+=1;self.state=fresh();self.history=[];self.future=[];self.dirty=False;self.sync();self.status.set('请选择起点。')
    def open(self):
        if not self.discard():return
        path=filedialog.askopenfilename(parent=self.root,filetypes=[('路线文件','*.json *.csv'),('所有文件','*.*')])
        if not path:return
        try:
            p=Path(path);text=p.read_text('utf-8-sig')
            if p.suffix.lower()=='.csv':
                points=parse_csv(text);state=fresh();state.update(name=p.stem,closed=False,points=points,reference=points,source='用户导入 WGS84 CSV',valid=True)
            else:state=project_data(json.loads(text))
            self.revision+=1;self.state=state;self.dirty=False;self.history=[];self.future=[]
            if state['points']:self.center=state['points'][0]
            self.sync();self.status.set('已打开；可导出原轨迹，或重新选点沿路规划。')
        except Exception as exc:messagebox.showerror('打开失败',str(exc),parent=self.root)
    def save(self):
        path=filedialog.asksaveasfilename(parent=self.root,defaultextension='.route.json',filetypes=[('工程','*.route.json')],initialfile='路线.route.json')
        if not path:return
        try:atomic(path,json.dumps(project_data(self.state),ensure_ascii=False,indent=2));self.dirty=False;self.status.set('工程已保存。')
        except Exception as exc:messagebox.showerror('保存失败',str(exc),parent=self.root)
    def export(self):
        if not self.state['valid']:self.status.set('当前修改尚未规划成功，不能导出。');return
        try:
            text=csv_text(self.state['points']);path=filedialog.asksaveasfilename(parent=self.root,defaultextension='.csv',filetypes=[('CSV','*.csv')],initialfile='路线.csv')
            if path:atomic(path,text);atomic(Path(path).with_suffix('.来源.txt'),self.state['source']);self.status.set('CSV 及来源说明已导出，可传到手机导入。')
        except Exception as exc:messagebox.showerror('导出失败',str(exc),parent=self.root)
    def close(self):
        if not self.discard():return
        atomic(HOME/'native-settings.json',json.dumps({'center':self.center,'zoom':self.zoom,'search_url':self.search_url}));self.closed=True;self.pool.shutdown(wait=False,cancel_futures=True);self.root.destroy()

def main():
    root=tk.Tk();app=App(root)
    if len(sys.argv)>2 and sys.argv[1]=='--smoke-test':
        def smoke():
            try:
                result={'ok':True,'native':'Tk','webview':False,'key_required':False,'widgets':len(root.winfo_children()),'canvas_width':app.canvas.winfo_width()}
                app.state['anchors']=[[114.328,30.511],[114.329,30.512]];app.render();result['markers']=app.list.size()
                pending=[];app.submit=lambda fn,done:pending.append(done)
                app.changed();app.changed()
                expected={'points':[[114.328,30.511],[114.329,30.512]],'source':SOURCE}
                pending[1](expected,None);pending[0]({'points':[[1,1],[2,2]],'source':'stale'},None)
                assert app.state['source']==SOURCE and app.state['valid']
                app.changed();pending[2](None,'测试网络失败')
                assert not app.state['valid'] and app.state['points']==expected['points']
                app.export();assert '不能导出' in app.status.get()
                app.checkpoint();app.state['anchors'].pop();app.undo();assert len(app.state['anchors'])==2
                result['stale_result_guard']=True;result['failure_export_guard']=True;result['undo']=True
                atomic(sys.argv[2],json.dumps(result))
            except Exception as exc:atomic(sys.argv[2],json.dumps({'ok':False,'error':str(exc)}))
            finally:app.dirty=False;app.close()
        root.after(800,smoke)
    root.mainloop()

if __name__=='__main__':main()
