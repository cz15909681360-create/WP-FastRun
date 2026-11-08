package com.zcshou.replay;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.widget.*;

final class PermissionTutorial {
  private PermissionTutorial() {}

  static void show(CampusActivity a, LinearLayout page) {
    page.removeAllViews();
    page.addView(MobileUi.text(a, "权限开启教程", 24));
    page.addView(MobileUi.text(a, "按顺序完成以下设置。无需开启 USB 或无线调试。", 14));
    card(
        a,
        page,
        "1 · 开启开发者选项",
        "设置 → 关于本机 / 关于手机 → 版本信息，连续点击“版本号”7次。根据系统提示完成锁屏验证，再在系统设置中搜索“开发者选项”。各机型菜单名称可能不同。",
        "前往关于本机",
        () -> open(a, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
    boolean mock = mockAllowed(a);
    card(
        a,
        page,
        "2 · 选择模拟位置应用",
        (mock ? "✓ 已选择WP快跑。" : "尚未选择WP快跑，或模拟位置未授权。")
            + "\n进入开发者选项 → 选择模拟位置信息应用 → WP快跑。此项需你在系统页面手动选择。若无法打开开发者选项，请先完成步骤1。",
        "打开开发者选项",
        () -> open(a, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
    card(
        a,
        page,
        "3 · 允许悬浮窗",
        (Settings.canDrawOverlays(a) ? "✓ 悬浮窗已允许。" : "悬浮窗尚未允许。")
            + "\n在“显示在其他应用上层 / 悬浮窗”页面找到WP快跑并允许。定位服务运行后，离开本应用会显示小卡片。",
        "开启悬浮窗权限",
        () ->
            open(
                a,
                new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + a.getPackageName()))));
    boolean fine =
        a.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
    card(
        a,
        page,
        "4 · 授予定位权限",
        (fine ? "✓ 精确位置权限已允许。" : "尚未授予精确位置权限。") + "\n在系统弹窗中允许定位，若有精确 / 大致选项，请选择精确。拒绝后可在应用权限设置中修改。",
        "授予定位权限",
        () -> {
          if (!fine)
            a.requestPermissions(
                new String[] {
                  android.Manifest.permission.ACCESS_FINE_LOCATION,
                  android.Manifest.permission.ACCESS_COARSE_LOCATION
                },
                12);
          else
            open(
                a,
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + a.getPackageName())));
        });
    MobileUi.button(
        page,
        "打开应用权限设置",
        () ->
            open(
                a,
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + a.getPackageName()))));
    MobileUi.button(page, "重新检查权限状态", () -> show(a, page));
    page.addView(MobileUi.text(a, "教程不会自动更改系统设置。授权完成后返回本应用，状态会自动刷新；地图和路线编辑不要求先启动定位服务。", 13));
  }

  private static boolean mockAllowed(android.content.Context c) {
    try {
      android.app.AppOpsManager m =
          (android.app.AppOpsManager) c.getSystemService(android.content.Context.APP_OPS_SERVICE);
      return m.checkOpNoThrow(
              android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
              android.os.Process.myUid(),
              c.getPackageName())
          == android.app.AppOpsManager.MODE_ALLOWED;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static void card(
      Activity a, LinearLayout parent, String title, String text, String action, Runnable r) {
    LinearLayout box = MobileUi.column(a);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xffffffff);
    bg.setCornerRadius(MobileUi.dp(a, 14));
    box.setBackground(bg);
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, MobileUi.dp(a, 8), 0, MobileUi.dp(a, 8));
    parent.addView(box, p);
    box.addView(MobileUi.text(a, title, 18));
    box.addView(MobileUi.text(a, text, 14));
    MobileUi.button(box, action, r);
  }

  private static void open(Activity a, Intent i) {
    try {
      a.startActivity(i);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(a, "此入口不可用，请在系统设置中搜索对应名称，按教程手动进入", Toast.LENGTH_LONG).show();
      try {
        a.startActivity(new Intent(Settings.ACTION_SETTINGS));
      } catch (ActivityNotFoundException ignored) {
      }
    }
  }
}
