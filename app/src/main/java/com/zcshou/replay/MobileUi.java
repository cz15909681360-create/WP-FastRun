package com.zcshou.replay;

import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import com.zcshou.service.ServiceGo;
import java.util.*;

final class MobileUi {
  static void window(Activity a) {
    a.getWindow().setStatusBarColor(BG);
    a.getWindow().setNavigationBarColor(BG);
    a.getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  static final int GREEN = 0xff087f74, BG = 0xfff3f6f8;

  static int dp(Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static LinearLayout column(Context c) {
    LinearLayout v = new LinearLayout(c);
    v.setOrientation(LinearLayout.VERTICAL);
    v.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
    v.setBackgroundColor(BG);
    return v;
  }

  static TextView text(Context c, String t, int size) {
    TextView v = new TextView(c);
    v.setText(t);
    v.setTextSize(size);
    v.setTextColor(0xff18332f);
    v.setPadding(0, dp(c, 6), 0, dp(c, 6));
    return v;
  }

  static Button button(LinearLayout parent, String label, Runnable action) {
    Button b = new Button(parent.getContext());
    b.setText(label);
    b.setTextColor(GREEN);
    b.setAllCaps(false);
    b.setMinHeight(dp(parent.getContext(), 48));
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(android.graphics.Color.WHITE);
    bg.setCornerRadius(dp(parent.getContext(), 12));
    bg.setStroke(dp(parent.getContext(), 1), 0xffc9ded9);
    b.setBackground(bg);
    b.setStateListAnimator(null);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    android.widget.LinearLayout.LayoutParams lp =
        parent.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, -2, 1)
            : new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(
        dp(parent.getContext(), 3),
        dp(parent.getContext(), 4),
        dp(parent.getContext(), 3),
        dp(parent.getContext(), 4));
    parent.addView(b, lp);
    b.setOnClickListener(v -> {
      try {
        action.run();
      } catch (RuntimeException e) {
        Toast.makeText(
                v.getContext(),
                e.getMessage() == null ? "操作未完成" : e.getMessage(),
                Toast.LENGTH_LONG)
            .show();
      }
    });
    style(b, label);
    return b;
  }

  static void style(Button b, String label) {
    boolean danger = label.contains("删除") || label.equals("停止") || label.contains("结束定位");
    boolean pause = label.equals("暂停");
    boolean primary =
        label.equals("开始")
            || label.equals("继续")
            || label.contains("保存")
            || label.equals("使用此路线")
            || label.equals("已开始，启动定位");
    String role = danger ? "danger" : pause ? "pause" : primary ? "primary" : "secondary";
    if (role.equals(b.getTag())) return;
    b.setTag(role);
    int fill = danger ? 0xffffeded : pause ? 0xffffd778 : primary ? GREEN : 0xffffffff;
    int text = danger ? 0xffad2424 : pause ? 0xff533d00 : primary ? 0xffffffff : 0xff42534f;
    android.graphics.drawable.GradientDrawable normal =
        new android.graphics.drawable.GradientDrawable();
    normal.setColor(fill);
    normal.setCornerRadius(dp(b.getContext(), 12));
    normal.setStroke(
        dp(b.getContext(), 1), danger ? 0xffd85353 : primary || pause ? fill : 0xffcedbd7);
    android.graphics.drawable.GradientDrawable pressed =
        new android.graphics.drawable.GradientDrawable();
    pressed.setColor(danger ? 0xffffcccc : pause ? 0xffeec05a : primary ? 0xff055f57 : 0xffe2efeb);
    pressed.setCornerRadius(dp(b.getContext(), 12));
    android.graphics.drawable.GradientDrawable disabled =
        new android.graphics.drawable.GradientDrawable();
    disabled.setColor(0xffe4e8e7);
    disabled.setCornerRadius(dp(b.getContext(), 12));
    android.graphics.drawable.StateListDrawable bg =
        new android.graphics.drawable.StateListDrawable();
    bg.addState(new int[] {-android.R.attr.state_enabled}, disabled);
    bg.addState(new int[] {android.R.attr.state_pressed}, pressed);
    bg.addState(new int[] {}, normal);
    b.setBackground(bg);
    b.setTextColor(
        new android.content.res.ColorStateList(
            new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xff788581, text}));
    b.setTextSize(primary ? 18 : danger || pause ? 16 : 14);
    b.setTypeface(
        null,
        primary || danger || pause
            ? android.graphics.Typeface.BOLD
            : android.graphics.Typeface.NORMAL);
    b.setMinHeight(dp(b.getContext(), primary ? 56 : 48));
    int tool=label.equals("撤销")?android.R.drawable.ic_media_rew:label.equals("重做")?android.R.drawable.ic_media_ff:label.equals("返回")?android.R.drawable.ic_media_previous:label.equals("编辑工具")?android.R.drawable.ic_menu_edit:label.equals("跟随位置")?android.R.drawable.ic_menu_mylocation:label.contains("全览")||label.equals("预览全部路线")?android.R.drawable.ic_menu_mapmode:0;
    if(tool!=0){android.graphics.drawable.Drawable icon=b.getContext().getDrawable(tool).mutate();icon.setTint(text);icon.setBounds(0,0,dp(b.getContext(),18),dp(b.getContext(),18));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(b.getContext(),3));b.setContentDescription(label);}

  }

  static boolean locked() {
    ServiceGo s = ServiceGo.current();
    return s != null && s.replayLocked();
  }

  static void editable() {
    if (locked()) throw new IllegalStateException("请先停止任务再编辑路线");
  }

  static RouteStore.Route find(Context c, long id) {
    try (RouteStore store = new RouteStore(c)) {
      for (RouteStore.Route r : store.list()) if (r.id == id) return r;
    }
    return RouteStore.nanhu();
  }

  static double metres(RouteStore.Route r) {
    List<ReplayEngine.Point> p = ReplayPanel.toWgs(r);
    double n = 0;
    for (int i = 1; i < p.size(); i++) n += ReplayEngine.metres(p.get(i - 1), p.get(i));
    return n;
  }

  static void edit(Activity a, RouteStore.Route r) {
    editable();
    Intent i = new Intent(a, MapEditorActivity.class);
    if (r != null) {
      double[] p = new double[r.points.size() * 2];
      for (int n = 0; n < r.points.size(); n++) {
        p[n * 2] = r.points.get(n).lat;
        p[n * 2 + 1] = r.points.get(n).lon;
      }
      i.putExtra("points", p)
          .putExtra("id", r.id)
          .putExtra("name", r.name)
          .putExtra("coordinateSystem", r.coordinateSystem.name());
    }
    a.startActivity(i);
  }

  static void detail(Activity a, long id) {
    a.startActivity(new Intent(a, RouteDetailActivity.class).putExtra("id", id));
  }

  static void routes(Activity a, LinearLayout list) {
    list.removeAllViews();
    list.addView(text(a, "我的路线", 24));
    button(list, "＋ 新建路线", () -> edit(a, null));
    button(
        list,
        "导入 CSV",
        () -> {
          editable();
          a.startActivity(new Intent(a, CsvImportActivity.class));
        });
    try (RouteStore store = new RouteStore(a)) {
      for (RouteStore.Route r : store.list()) {
        String summary =
            String.format(
                Locale.CHINA,
                "%s%s\n%.3f km · %d 点",
                r.name,
                r.builtinKey != null ? "  · 内置" : "",
                metres(r) / 1000,
                r.points.size());
        button(list, summary, () -> detail(a, r.id));
      }
    }
  }
}
