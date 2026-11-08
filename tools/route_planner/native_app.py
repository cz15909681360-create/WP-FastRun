"""Qt native straight-line editor."""
import sys,json,math,time,re
from pathlib import Path
from PySide6.QtCore import Qt,QTimer,QPointF,QStringListModel
from PySide6.QtGui import QColor,QPen,QPainterPath,QUndoStack,QUndoCommand,QKeySequence,QPixmap
from PySide6.QtWidgets import *
from PySide6.QtNetwork import QNetworkAccessManager,QNetworkRequest
from PySide6.QtCore import QUrl
from main import HOME,atomic,distance,parse_csv,csv_text
from coordinates import convert,SYSTEMS

def route_filename(name, suffix):
    """Use the route title as a safe Windows filename, without changing the title."""
    stem=re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', name.strip()).rstrip(' .') or '路线'
    reserved={'CON','PRN','AUX','NUL'} | {f'{prefix}{i}' for prefix in ('COM','LPT') for i in range(1,10)}
    if stem.split('.')[0].upper() in reserved:stem='_'+stem
    return stem+suffix

def parse_document(text):
    if len(text.encode('utf-8'))>2*1024*1024:raise ValueError('文件超过 2 MiB')
    text=text.lstrip('\ufeff');system='WGS84';lines=text.splitlines()
    if any(r.strip().lower().startswith('# coordinatesystem') for r in lines):raise ValueError('不能用注释声明坐标系')
    if lines and lines[0].startswith('coordinateSystem,'):
        row=lines.pop(0).split(',')
        if len(row)!=2:raise ValueError('无效坐标系声明')
        system=row[1]
        if system not in SYSTEMS:raise ValueError('未知坐标系')
    return system,parse_csv('\n'.join(lines))


def geometry(points,closed):
    result=[list(p) for p in points]
    for p in result:
        if len(p)!=2 or not all(isinstance(v,(int,float)) and math.isfinite(v) for v in p) or not -180<=p[0]<=180 or not -90<=p[1]<=90:raise ValueError('无效 WGS84 坐标')
    if closed and len(result)>1 and result[-1]!=result[0]:result.append(result[0])
    if len(result)>10000:raise ValueError('闭合需预留起点坐标，最多导出 10000 点')
    return result

def xy(p):
    lon,lat=p;lat=max(-85,min(85,lat));n=256*2**19
    return QPointF((lon+180)/360*n,(1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*n)
def ll(p):
    n=256*2**19;return [p.x()/n*360-180,math.degrees(math.atan(math.sinh(math.pi*(1-2*p.y()/n))))]

class Change(QUndoCommand):
    def __init__(self,app,kind,index=None,before=None,after=None):super().__init__('编辑路线');self.a=app;self.kind=kind;self.i=index;self.b=before;self.v=after
    def apply(self,forward):
        a=self.a;p=a.points;k=self.kind;v=self.v if forward else self.b
        if k=='move':p[self.i]=v
        elif k=='insert':p.insert(self.i,self.v) if forward else p.pop(self.i)
        elif k=='delete':p.pop(self.i) if forward else p.insert(self.i,self.b)
        elif k=='swap':p[self.i],p[self.v]=p[self.v],p[self.i]
        elif k=='closed':a.closed=v
        a.refresh()
    def redo(self):self.apply(True)
    def undo(self):self.apply(False)

from tencent_map import Map

class App(QMainWindow):
    def __init__(self):
        super().__init__();self.reference_crs='GCJ02';self.crs='GCJ02';self.points=[];self.closed=True;self.selected=-1;self.reference=[];self.stack=QUndoStack(self);self.setWindowTitle('校园路线规划器');self.resize(1280,820);self.setMinimumSize(960,640)
        self.setStyleSheet('QMainWindow,QWidget{background:#f5f7fa;color:#233d50;font-family:Microsoft YaHei UI;font-size:13px} QToolBar{background:white;border:0;padding:12px;spacing:8px} QPushButton,QToolButton{background:white;border:1px solid #dce5eb;border-radius:7px;padding:9px 14px} QPushButton:hover,QToolButton:hover{background:#e5f4f2} QLineEdit,QDoubleSpinBox,QListView{background:white;border:1px solid #e0e7ec;border-radius:7px;padding:8px} QListView::item{padding:9px} QListView::item:selected{background:#ddf2ef;color:#167b78} QGroupBox{border:0;margin-top:8px} QLabel#title{font-size:23px;font-weight:600} QLabel#metric{font-size:30px;color:#168a87;font-weight:600}')
        bar=self.addToolBar('路线');bar.setMovable(False)
        for name,fn in [('新建',self.new),('打开',self.open),('保存',self.save)]:bar.addAction(name,fn)
        bar.addAction(self.stack.createUndoAction(self,'撤销'));bar.actions()[-1].setShortcut(QKeySequence.StandardKey.Undo);bar.addAction(self.stack.createRedoAction(self,'重做'));bar.actions()[-1].setShortcut(QKeySequence.StandardKey.Redo)
        self.closedbtn=QPushButton('● 闭合路线');self.openbtn=QPushButton('○ 不闭合路线');bar.addWidget(self.closedbtn);bar.addWidget(self.openbtn);self.closedbtn.clicked.connect(lambda:self.mode(True));self.openbtn.clicked.connect(lambda:self.mode(False));spacer=QWidget();spacer.setSizePolicy(QSizePolicy.Policy.Expanding,QSizePolicy.Policy.Preferred);bar.addWidget(spacer);export=QPushButton('导出 CSV ↗');export.setStyleSheet('background:#168a87;color:white;border:0');export.clicked.connect(self.export);bar.addWidget(export)
        body=QWidget();row=QHBoxLayout(body);row.setContentsMargins(18,18,18,18);row.setSpacing(18);side=QWidget();side.setFixedWidth(300);col=QVBoxLayout(side);col.setContentsMargins(0,0,0,0)
        settings=QPushButton('腾讯地图设置 / 重试');settings.clicked.connect(lambda:self.map.configure());bar.addWidget(settings);sat=QPushButton('标准 / 卫星');sat.setCheckable(True);sat.toggled.connect(lambda on:self.map.page().runJavaScript('satellite('+str(on).lower()+')'));bar.addWidget(sat)
        title=QLabel('绘制你的路线');title.setObjectName('title');col.addWidget(title);col.addWidget(QLabel('点击选点 · 顺序直连 · 拖动调整'))
        self.name=QLineEdit('我的校园路线');col.addWidget(self.name);self.name.textEdited.connect(lambda:self.setWindowModified(True));self.distance_label=QLabel('0.00 km');self.distance_label.setObjectName('metric');col.addWidget(self.distance_label);self.count=QLabel();col.addWidget(self.count)
        self.target=QGroupBox('距离目标');self.target.setCheckable(True);self.target.setChecked(False);tl=QVBoxLayout(self.target);self.goal=QDoubleSpinBox();self.goal.setRange(0,100000);self.goal.setSuffix(' km');self.goal.setValue(3);tl.addWidget(self.goal);self.delta=QLabel();tl.addWidget(self.delta);self.target.toggled.connect(lambda v:(self.goal.setVisible(v),self.delta.setVisible(v)));self.goal.hide();self.delta.hide();self.goal.valueChanged.connect(lambda:(self.setWindowModified(True),self.refresh(False)));col.addWidget(self.target)
        self.searchbox=QLineEdit();self.searchbox.setPlaceholderText('搜索地点 / 经度,纬度');self.searchbox.returnPressed.connect(self.search);col.addWidget(self.searchbox)
        searchbutton=QPushButton('搜索地点');searchbutton.clicked.connect(self.search);col.addWidget(searchbutton)
        self.searchcache={};self.lastsearch=0;self.searchnet=QNetworkAccessManager(self)
        self.model=QStringListModel();self.list=QListView();self.list.setModel(self.model);self.list.clicked.connect(lambda ix:self.select(ix.row()));col.addWidget(self.list,1)
        buttons=QHBoxLayout()
        for label,fn in [('↑',lambda:self.swap(-1)),('↓',lambda:self.swap(1)),('删除',self.delete)]:b=QPushButton(label);b.clicked.connect(fn);buttons.addWidget(b)
        col.addLayout(buttons);col.addWidget(QLabel('选中点 · 精确坐标'));self.lon=QDoubleSpinBox();self.lat=QDoubleSpinBox()
        for field,limit,prefix in [(self.lon,180,'经度 '),(self.lat,90,'纬度 ')]:field.setRange(-limit,limit);field.setDecimals(8);field.setPrefix(prefix);field.editingFinished.connect(self.edit);col.addWidget(field)
        col.addWidget(QLabel('腾讯地图 · 直线编辑'));row.addWidget(side);self.map=Map(self);row.addWidget(self.map,1);self.setCentralWidget(body);self.statusBar().showMessage('点击地图添加点；拖动空白处平移，滚轮围绕鼠标缩放。');self.refresh()
    def drawpoints(self):return geometry(self.points,self.closed)
    def search(self):
        text=self.searchbox.text().strip()
        try:
            p=list(map(float,text.replace('，',',').split(',')))
            if len(p)==2:self.map.centerOn(xy(p));return
        except ValueError:pass
        self.map.search(text)
    def refresh(self,listing=True):
        self.map.draw();p=self.drawpoints();meters=distance([convert(v,self.crs,'WGS84') for v in p]);self.distance_label.setText(f'{meters/1000:.2f} km');self.count.setText(f'{self.crs} · {len(self.points)} 个点  ·  {"闭合" if self.closed else "不闭合"}');self.delta.setText(f'距离目标差 {(meters-self.goal.value()*1000):+.0f} 米');self.closedbtn.setChecked(self.closed);self.closedbtn.setStyleSheet('background:#ddf2ef' if self.closed else '');self.openbtn.setStyleSheet('background:#ddf2ef' if not self.closed else '')
        if listing:self.model.setStringList([f'{i+1:03d}    {p[1]:.5f}, {p[0]:.5f}' for i,p in enumerate(self.points)])
    def select(self,i):
        self.selected=i
        if 0<=i<len(self.points):self.lon.setValue(self.points[i][0]);self.lat.setValue(self.points[i][1]);self.list.setCurrentIndex(self.model.index(i))
        self.map.draw()
    def insert(self,i,p):
        try:geometry(self.points+[p],self.closed)
        except ValueError as e:return self.statusBar().showMessage(str(e))
        self.stack.push(Change(self,'insert',i,after=p));self.select(i)
    def mode(self,v):
        try:geometry(self.points,v)
        except ValueError as e:return self.statusBar().showMessage(str(e))
        if self.closed!=v:self.stack.push(Change(self,'closed',before=self.closed,after=v))
    def edit(self):
        i=self.selected
        if 0<=i<len(self.points):
            p=[self.lon.value(),self.lat.value()]
            if p!=self.points[i]:self.stack.push(Change(self,'move',i,self.points[i],p))
    def swap(self,d):
        i=self.selected
        if 0<=i+d<len(self.points) and i>=0:self.stack.push(Change(self,'swap',i,after=i+d));self.select(i+d)
    def delete(self):
        if 0<=self.selected<len(self.points):self.stack.push(Change(self,'delete',self.selected,before=self.points[self.selected]));self.select(min(self.selected,len(self.points)-1))
    def discard(self):return self.stack.isClean() and not self.isWindowModified() or QMessageBox.question(self,'未保存','放弃未保存修改？')==QMessageBox.StandardButton.Yes
    def new(self):
        if self.discard():self.reference_crs='GCJ02';self.crs='GCJ02';self.points=[];self.reference=[];self.closed=True;self.selected=-1;self.name.setText('我的校园路线');self.goal.setValue(3);self.stack.clear();self.setWindowModified(False);self.refresh()
    def open(self):
        if not self.discard():return
        path,_=QFileDialog.getOpenFileName(self,'打开路线','','路线 (*.csv *.json)')
        if not path:return
        try:
            old_crs=self.crs;old_reference=self.reference;old_reference_crs=self.reference_crs
            text=Path(path).read_text('utf-8-sig');data=None;self.crs='WGS84';self.reference=[];self.reference_crs='WGS84'
            if path.lower().endswith('.csv'):
                self.crs,points=parse_document(text);closed=len(points)>1 and points[0]==points[-1];points=points[:-1] if closed else points
            else:
                data=json.loads(text)
                if data.get('version') in (2,3):
                    self.crs=data['coordinateSystem'] if data['version']==3 else 'WGS84'
                    if self.crs not in SYSTEMS:raise ValueError('未知坐标系')
                    points=data['anchors'];closed=data['closed'];self.reference=data.get('reference',[]);self.reference_crs=data.get('referenceCoordinateSystem',self.crs)
                    if self.reference_crs not in SYSTEMS:raise ValueError('未知参照坐标系')
                elif data.get('version')==1:
                    points=data.get('points',[]);closed=False
                    if len(data.get('anchors',[]))>=2 and QMessageBox.question(self,'旧路网工程','是否按原途经点转成直线？选“否”保留完整原轨迹编辑。')==QMessageBox.StandardButton.Yes:self.reference=points;points=data['anchors'];closed=data.get('closed',True)
                else:raise ValueError('不支持的工程版本')
            geometry(points,closed);self.points=points;self.closed=closed;self.stack.clear();self.setWindowModified(False)
            if data:self.name.setText(data.get('name','我的校园路线'));self.goal.setValue(data.get('target',3))
            self.refresh()
            if points:self.map.centerOn(xy(points[0]))
        except Exception as e:
            if 'old_crs' in locals():self.crs=old_crs;self.reference=old_reference;self.reference_crs=old_reference_crs
            QMessageBox.warning(self,'打开失败',str(e))
    def save(self):
        path,_=QFileDialog.getSaveFileName(self,'保存工程',route_filename(self.name.text(),'.route.json'),'工程 (*.route.json)')
        if path:atomic(path,json.dumps(dict(version=3,coordinateSystem=self.crs,referenceCoordinateSystem=self.reference_crs,mode='straight',name=self.name.text(),anchors=self.points,closed=self.closed,points=self.drawpoints(),reference=self.reference,target=self.goal.value()),ensure_ascii=False));self.stack.setClean();self.setWindowModified(False)
    def export(self):
        try:
            text=('coordinateSystem,'+self.crs+'\n' if self.crs!='WGS84' else '')+csv_text(self.drawpoints());path,_=QFileDialog.getSaveFileName(self,'导出手机 CSV',route_filename(self.name.text(),'.csv'),'CSV (*.csv)')
            if path:atomic(path,text);atomic(Path(path).with_suffix('.来源.txt'),'用户选点直线；底图 腾讯地图')
        except Exception as e:QMessageBox.warning(self,'无法导出',str(e))
    def closeEvent(self,e):
        if self.discard():self.map.shutdown();e.accept()
        else:e.ignore()

def main():
    app=QApplication(sys.argv);window=App();window.show()
    if '--smoke-test' in sys.argv:
        def smoke():
            window.points=[[114.328,30.511],[114.329,30.512]];window.refresh();assert len(window.drawpoints())==3;window.mode(False);assert len(window.drawpoints())==2;window.stack.undo();assert window.closed;window.grab().save(str(Path(sys.argv[2]).with_suffix('.png')));atomic(sys.argv[2],json.dumps({'ok':True,'native':'Qt Widgets + Tencent GL','straight':True}));window.map.shutdown();app.quit()
        QTimer.singleShot(500,smoke)
    app.exec()
if __name__=='__main__':main()

