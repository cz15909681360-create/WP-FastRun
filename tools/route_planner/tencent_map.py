"""Tencent GL in loopback-hosted QtWebEngine; SK is never exposed to JavaScript."""
import base64,ctypes,hashlib,json,math,threading,urllib.parse,urllib.request
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from PySide6.QtCore import QObject,Signal,Slot,QUrl,QPointF,QFile,QIODevice
from PySide6.QtWebEngineWidgets import QWebEngineView
from PySide6.QtWebChannel import QWebChannel
from PySide6.QtWidgets import QDialog,QFormLayout,QLineEdit,QDialogButtonBox,QMessageBox
from main import HOME
from coordinates import convert

def crypt(raw,decode=False):
 class Blob(ctypes.Structure):_fields_=[('n',ctypes.c_ulong),('p',ctypes.POINTER(ctypes.c_byte))]
 buf=ctypes.create_string_buffer(raw);src=Blob(len(raw),ctypes.cast(buf,ctypes.POINTER(ctypes.c_byte)));out=Blob()
 fn=ctypes.windll.crypt32.CryptUnprotectData if decode else ctypes.windll.crypt32.CryptProtectData
 if not fn(ctypes.byref(src),None,None,None,None,1,ctypes.byref(out)):raise OSError('凭据加密失败')
 try:return ctypes.string_at(out.p,out.n)
 finally:ctypes.windll.kernel32.LocalFree(out.p)
class Bridge(QObject):
 updated=Signal(str)
 @Slot()
 def ready(self):self.parent().draw()
 @Slot(int)
 def select(self,index):self.parent().a.select(index)
 @Slot(str)
 def edit(self,text):
  try:
   e=json.loads(text);a=self.parent().a;raw=e['point']
   if len(raw)!=2 or not all(isinstance(v,(int,float)) and math.isfinite(v) for v in raw) or not -180<=raw[0]<=180 or not -90<=raw[1]<=90:raise ValueError('无效地图坐标')
   if e['type']=='move' and not 0<=e['index']<len(a.points):raise ValueError('无效点索引')
   p=convert(raw,'GCJ02',a.crs)
   from native_app import Change
   if e['type']=='move':a.stack.push(Change(a,'move',e['index'],a.points[e['index']],p))
   else:a.insert(e.get('index',len(a.points)),p)
  except Exception as exc:self.parent().a.statusBar().showMessage(str(exc))
 @Slot(str)
 def status(self,text):self.parent().a.statusBar().showMessage(text[:300])
PAGE='''<!doctype html><meta charset="utf-8"><style>html,body,#map{height:100%;margin:0}#error{position:absolute;top:20px;left:20px;background:white;padding:15px;z-index:1000;font:14px sans-serif}</style><div id="map"></div><div id="error">地图加载中…</div><script src="/qtwebchannel.js"></script><script>
let bridge,map,markers,lines,state;const err=document.getElementById('error');
const loadingTimeout=setTimeout(()=>{if(err.style.display!=='none')err.textContent='地图加载超时，请点击“腾讯地图设置 / 重试”重试';},20000);
window.addEventListener('error',()=>{clearTimeout(loadingTimeout);err.style.display='block';err.textContent='地图脚本执行失败，请点击“腾讯地图设置 / 重试”重试';});
function draw(raw){state=JSON.parse(raw);if(!map)return;const ps=state.points.map(p=>new TMap.LatLng(p[1],p[0]));let route=state.closed&&ps.length>1?ps.concat([ps[0]]):ps;
 let geoms=route.length>1?[{id:'route',styleId:'line',paths:route}]:[];if(state.reference&&state.reference.length>1)geoms.push({id:'reference',styleId:'ref',paths:state.reference.map(p=>new TMap.LatLng(p[1],p[0]))});lines.setGeometries(geoms);
 markers.setGeometries(ps.map((p,i)=>({id:String(i),position:p,properties:{index:i}})).filter((p,i)=>ps.length<200||i===0||i===ps.length-1||i===state.selected));}
function send(type,index,p){bridge.edit(JSON.stringify({type,index,point:[p.lng,p.lat]}));}
new QWebChannel(qt.webChannelTransport,c=>{bridge=c.objects.bridge;bridge.updated.connect(draw);bridge.ready();
const script=document.createElement('script');script.src='https://map.qq.com/api/gljs?v=1.exp&key=__KEY__';script.onerror=()=>{clearTimeout(loadingTimeout);err.textContent='腾讯地图加载失败，请检查网络或配置 Key 后重试';};
script.onload=()=>{try{map=new TMap.Map(document.getElementById('map'),{center:new TMap.LatLng(30.511,114.328),zoom:16,viewMode:'2D'});
lines=new TMap.MultiPolyline({map,styles:{line:new TMap.PolylineStyle({color:'#168a87',width:5}),ref:new TMap.PolylineStyle({color:'#9daab4',width:3})},geometries:[]});
markers=new TMap.MultiMarker({map,styles:{default:new TMap.MarkerStyle({width:22,height:30,anchor:{x:11,y:30}})},geometries:[],enableDragging:true});
let layerClick=0;map.on('click',e=>{if(Date.now()-layerClick>150)send('insert',state?state.points.length:0,e.latLng);});markers.on('click',e=>{layerClick=Date.now();bridge.select(Number(e.geometry.id));});markers.on('dragend',e=>send('move',Number(e.geometry.id),e.latLng));
lines.on('click',e=>{layerClick=Date.now();if(!state)return;let best=0,gap=Infinity;const p=map.projectToContainer(e.latLng);let n=state.closed?state.points.length:state.points.length-1;for(let i=0;i<n;i++){let a=map.projectToContainer(new TMap.LatLng(state.points[i][1],state.points[i][0])),b=map.projectToContainer(new TMap.LatLng(state.points[(i+1)%state.points.length][1],state.points[(i+1)%state.points.length][0]));let dx=b.x-a.x,dy=b.y-a.y,t=Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/(dx*dx+dy*dy||1))),d=Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);if(d<gap){gap=d;best=i;}}send('insert',best+1,e.latLng);});
clearTimeout(loadingTimeout);err.style.display='none';if(state)draw(JSON.stringify(state));bridge.status('腾讯地图已加载');}catch(e){clearTimeout(loadingTimeout);err.textContent='地图初始化失败：'+(e.message||String(e));}};document.head.appendChild(script);});
function satellite(on){if(map)map.setBaseMap(on?{type:'satellite',features:['base','road']}:{type:'vector'});}
function center(lon,lat){if(map)map.setCenter(new TMap.LatLng(lat,lon));}
</script>'''
class Map(QWebEngineView):
 searchResult=Signal(object)
 def __init__(self,a):
  super().__init__();self.a=a;self.key='';self.sk='';self.search_version=0;self.searchResult.connect(self.found)
  try:self.key,self.sk=json.loads(crypt(base64.b64decode((HOME/'tencent.credentials').read_bytes()),True))
  except Exception:pass
  self.channel=QWebChannel(self.page());self.bridge=Bridge(self);self.channel.registerObject('bridge',self.bridge);self.page().setWebChannel(self.channel)
  channel_file=QFile(':/qtwebchannel/qwebchannel.js')
  if not channel_file.open(QIODevice.OpenModeFlag.ReadOnly):raise RuntimeError('无法读取 Qt 地图通信脚本')
  channel_script=bytes(channel_file.readAll());channel_file.close()
  owner=self
  class Handler(BaseHTTPRequestHandler):
   def do_GET(s):
    if s.path=='/qtwebchannel.js':
     s.send_response(200);s.send_header('Content-Type','application/javascript; charset=utf-8');s.send_header('Content-Length',str(len(channel_script)));s.end_headers();s.wfile.write(channel_script);return
    if s.path!='/':s.send_error(404);return
    text=PAGE.replace('__KEY__',urllib.parse.quote(owner.key,safe='')) if owner.key else '<p style="font:16px sans-serif;padding:30px">请在“腾讯地图设置”填写 Key 和 SK。路线文件编辑仍可使用。</p>'
    raw=text.encode();s.send_response(200);s.send_header('Content-Type','text/html; charset=utf-8');s.end_headers();s.wfile.write(raw)
   def log_message(self,*args):pass
  self.server=ThreadingHTTPServer(('127.0.0.1',0),Handler);self.server.daemon_threads=True;threading.Thread(target=self.server.serve_forever,daemon=True).start();self.url=QUrl('http://127.0.0.1:'+str(self.server.server_port)+'/');self.setUrl(self.url);self.loadFinished.connect(lambda ok:self.draw());self._closed=False
 def draw(self):
  self.bridge.updated.emit(json.dumps({'points':[convert(p,self.a.crs,'GCJ02') for p in self.a.points],'closed':self.a.closed,'selected':self.a.selected,'reference':[convert(p,getattr(self.a,'reference_crs',self.a.crs),'GCJ02') for p in self.a.reference]}))
 def centerOn(self,p):
  from native_app import ll
  g=convert(ll(p),self.a.crs,'GCJ02');self.page().runJavaScript('center('+','.join(map(str,g))+')')
 def configure(self):
  d=QDialog(self.a);d.setWindowTitle('腾讯地图设置');f=QFormLayout(d);key=QLineEdit(self.key);sk=QLineEdit(self.sk);key.setEchoMode(QLineEdit.EchoMode.Password);sk.setEchoMode(QLineEdit.EchoMode.Password);f.addRow('Key',key);f.addRow('签名 SK',sk);buttons=QDialogButtonBox(QDialogButtonBox.StandardButton.Save|QDialogButtonBox.StandardButton.Cancel);f.addRow(buttons);buttons.accepted.connect(d.accept);buttons.rejected.connect(d.reject)
  if d.exec():self.key=key.text().strip();self.sk=sk.text().strip();HOME.mkdir(parents=True,exist_ok=True);(HOME/'tencent.credentials').write_bytes(base64.b64encode(crypt(json.dumps([self.key,self.sk]).encode())));self.setUrl(self.url)
 def search(self,text):
  self.search_version+=1;version=self.search_version
  if not self.key or not self.sk:self.a.statusBar().showMessage('请配置 Key 和签名 SK');return
  def work():
   try:
    path='/ws/place/v1/search';params={'keyword':text,'boundary':'region(武汉,0)','key':self.key};ordered='&'.join(k+'='+params[k] for k in sorted(params));sig=hashlib.md5((path+'?'+ordered+self.sk).encode()).hexdigest();url='https://apis.map.qq.com'+path+'?'+urllib.parse.urlencode(params)+'&sig='+sig
    with urllib.request.urlopen(url,timeout=15) as r:data=json.load(r)
    self.searchResult.emit((version,data))
   except Exception:self.searchResult.emit((version,{'status':-1,'message':'网络请求失败，请重试'}))
  threading.Thread(target=work,daemon=True).start()
 def found(self,result):
  version,data=result
  if version!=self.search_version:return
  if data.get('status')!=0:self.a.statusBar().showMessage('腾讯搜索失败：'+str(data.get('message','鉴权或额度错误')));return
  if not data.get('data'):self.a.statusBar().showMessage('未找到地点');return
  p=data['data'][0]['location'];self.page().runJavaScript(f"center({p['lng']},{p['lat']})")
 def shutdown(self):
  if not self._closed:self._closed=True;self.server.shutdown();self.server.server_close()
