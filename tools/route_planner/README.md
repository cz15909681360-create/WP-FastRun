# 电脑与手机统一坐标协议（腾讯地图版）

## 数据

新腾讯路线：GCJ02 原值编辑、保存、交换。旧 WGS84、BD09 保留数值和标记。南湖既有四点仍为历史 WGS84，不根据地图观感自动改标。

桌面内部 [longitude,latitude]，Android Point(lat,lon)，CSV 纬度在前。新工程 v3，coordinateSystem 必填 WGS84/GCJ02/BD09，referenceCoordinateSystem 标记参照。v1/v2 沿历史 WGS84 读取。

GCJ02 CSV：

```csv
coordinateSystem,GCJ02
latitude,longitude
30.51100000,114.32800000
30.51200000,114.32900000
```

未知、重复或冲突声明拒绝。不能用 # 注释声明坐标系。旧无声明 CSV 默认 WGS84。旧手机遇新首行必须失败，不允许静默当 WGS84。

## 转换

两端采用相同常量、区域边界和八次迭代逆算；位置、距离仅在复演边界归一至 WGS84。GCJ02 在腾讯地图恒等显示。旧路线未修改点不反复转换。数值自洽及往返测试不代表实地精度核验。

固定样本：routes/coordinate-contract/fixtures.json。CSV 样本：routes/coordinate-contract/sample-gcj02.csv。手机测试：CoordinateContractTest。共享样本含南湖、北京、上海、境外。

## 腾讯配置

打开桌面“腾讯地图设置 / 重试”，填 Key 与 SK，保存在本机 Windows DPAPI 加密文件。JavaScript API 创建 Key 即可使用；WebServiceAPI 选择签名校验取得 SK。截图中的域名白名单是 JSONP 使用方式，不是 Python 签名请求配置。SK 不发给网页，不写源码或路线文件。仅免费额度，不自动开付费。

地图通信脚本通过回环 HTTP 服务的 `/qtwebchannel.js` 路径加载；HTTP 页面不能直接加载 `qrc:///qtwebchannel/qwebchannel.js`，否则会停在“地图加载中”。网络或初始化失败时页面显示错误，20 秒仍未完成则显示超时提示。可运行 `python tools/route_planner/diagnose_map.py` 验证本机保存的 Key 下的实际地图初始化；该检查不打印凭据，有效地图与覆盖物初始化成功时退出码为 0。可追加截图输出路径保存程序自身渲染的画面。

地图在 QtWebEngine 内嵌，以回环 HTTP 加载。标准与卫星可切换，不打开外部浏览器。搜索由 Python 签名请求。地图不可用时仍可编辑列表、保存工程及导出 CSV；无 OSM 自动备用。现阶段搜索围绕武汉城市查询。

## 交付与状态

桌面：deliverables/CampusRoutePlanner.exe。
手机：deliverables/CampusRouteReplay-GCJ02-1.12.5.apk（保留既有包名、签名；新 GCJ02 CSV 请使用此更新版本）。

自动检查：Android assembleDebug、testDebugUnitTest 成功，27 项测试无失败，其中坐标协议新增4项；桌面16项测试及共享固定点、三种CSV、非法声明、DPAPI往返检查成功；源码嵌入窗口离线启动自检通过。

待完成验收：有效腾讯 Key/SK 下地图/WebGL/卫星/点选拖动/搜索/鉴权额度错误；手机真实导入、SQLite历史数据及旋转恢复、实际定位输出。尚未将这些记为通过；固定样本未做实地采集。

命令：

```powershell
python -m unittest discover -s tools/route_planner -p "test_*.py"
C:/Users/25301/.gradle/manual/gradle-8.13/bin/gradle.bat :app:assembleDebug :app:testDebugUnitTest --offline
```
