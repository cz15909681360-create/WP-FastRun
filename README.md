# WP快跑

Android 校园路线编辑与模拟定位复演应用，包含配套电脑路线规划器。当前版本 **1.12.10 / 1130**，包名 `com.zcshou.yinsuo.replay`。

## 下载

[下载已签名 APK](https://github.com/cz15909681360-create/WP-FastRun/releases/latest)

## 功能与源码

- 腾讯地图查看、手机绘制和编辑路线，内置南湖与余家头路线。
- CSV 模板导出、表格与地图预览、坐标系校验后导入。
- 圈数 / 目标距离复演、调速、暂停继续与后台悬浮卡片；默认 16 km/h。
- 权限教程、运行亮屏和路线完成提示。
- 电脑路线规划器位于 `tools/route_planner`，坐标协议及测试数据位于 `routes/coordinate-contract`。

## Android 构建

需要 JDK 17 或更新版本、Android SDK Platform 36、Build Tools 36.0.0。最低 Android 8.1，targetSdk 32，arm64。

1. 将 `local.properties.example` 复制为 `local.properties`，设置 SDK 路径和自己的 `TENCENT_MAP_KEY`。
2. 腾讯位置服务 Key 启用“地图 SDK”，绑定包名 `com.zcshou.yinsuo.replay`。地图未配置时仍可使用已有路线和 CSV 管理。
3. 执行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows 使用 `gradlew.bat`。产物位于 `app/build/outputs/apk/debug`。

**签名说明：** 本仓库不包含私钥。自行构建的 debug APK 使用本机标准调试签名，不能直接覆盖发布页的已有签名版本。发布版可复制 `signing.properties.example` 为 `signing.properties` 后配置自己的签名；未配置时 release 产物不签名。公开源码的签名配置与本地发布工程分离，现有发布 APK 未改变。

## 电脑路线规划器

```sh
python -m pip install -r tools/route_planner/requirements.txt
python tools/route_planner/main.py
python -m unittest discover -s tools/route_planner -p "test_*.py"
```

详情见 [电脑端说明](tools/route_planner/README.md)、[Android 使用说明](REPLAY_GUIDE.md)。桌面地图凭据保存在本机，不随源码提交。

## 验证状态

此前本地构建、42 项 Android 单元测试、lint、签名与 APK / ELF 对齐检查通过。WP 名称和图片图标改版已重新构建并检查签名、APK 对齐。OPPO 真机安装、地图及跨应用交互仍需手动验收，未作为通过项。

## 来源与许可

基于 [hackerc5/YinSuo](https://github.com/hackerc5/YinSuo) 修改，保留原作者信息及 [GPL-3.0 许可证](LICENSE)。原版说明见 [UPSTREAM_README](docs/UPSTREAM_README.md)。腾讯 / 百度等第三方 SDK 仍适用其各自的许可与服务条款。

不提交地图 Key、签名私钥、SDK 本地路径、缓存、日志及历史构建产物；APK 通过 Releases 单独分发。
