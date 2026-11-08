package com.zcshou.replay;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.zcshou.service.ServiceGo;
import java.util.*;

public final class CampusActivity extends Activity {
  private ImageButton followTool;
  private LinearLayout controls, root, runPage, routeList, settingsPage, tutorialPage;
  private FrameLayout pages;
  private android.view.View routePage, settingsScroll, tutorialScroll;
  private TextView stats;
  private TencentMapPane pane;
  private Button primary, stop;
  private boolean bound, follow = true;
  private ServiceGo service;
  private long selectedId = -1, mappedId = Long.MIN_VALUE;
  private int tab;
  private double localSpeed = 16, localGoal = 1;
  private int localMode;
  private boolean pendingConfig;
  private final android.view.View[] navigation = new android.view.View[4];
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          render();
          handler.postDelayed(this, 500);
        }
      };
  private final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder binder) {
          service = ((ServiceGo.ServiceGoBinder) binder).getService();
          prepare();
          render();
        }

        public void onServiceDisconnected(ComponentName n) {
          service = null;
          bound = false;
          render();
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    MobileUi.window(this);
    setTitle("WP快跑");
    if (state != null) {
      selectedId = state.getLong("selected", -1);
      tab = state.getInt("tab");
    }
    if (getIntent().hasExtra("selected_route_id"))
      selectedId = getIntent().getLongExtra("selected_route_id", -1);
    root = MobileUi.column(this);
    setContentView(root);
    pages = new FrameLayout(this);
    root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
    runPage = MobileUi.column(this);
    pages.addView(runPage, new FrameLayout.LayoutParams(-1, -1));
    pane =
        new TencentMapPane(
            this,
            () -> {
              mappedId = Long.MIN_VALUE;
              pane.view.setOnTouchListener((v, e) -> {
                if (e.getAction() == 2) follow = false;
                return false;
              });
            });
    runPage.addView(pane, new LinearLayout.LayoutParams(-1, 0, 1));
    runPage.removeView(pane);
    FrameLayout mapArea = new FrameLayout(this);
    runPage.addView(mapArea, new LinearLayout.LayoutParams(-1, 0, 1));
    mapArea.addView(pane, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout tools = new LinearLayout(this);
    tools.setOrientation(LinearLayout.VERTICAL);
    FrameLayout.LayoutParams toolPosition =
        new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
    toolPosition.setMargins(0, MobileUi.dp(this, 12), MobileUi.dp(this, 12), 0);
    mapArea.addView(tools, toolPosition);
    ImageButton full =
        mapTool(
            tools,
            "路线全览",
            com.zcshou.gogogo.R.drawable.ic_fit_outline,
            () -> {
              follow = false;
              pane.fit(ReplayPanel.toWgs(currentRoute()));
            });
    followTool =
        mapTool(
            tools,
            "跟随位置",
            com.zcshou.gogogo.R.drawable.ic_follow_outline,
            () -> {
              follow = true;
              if (service != null) pane.position(service.replaySnapshot().point, true);
            });
    stats = MobileUi.text(this, "准备就绪", 18);
    runPage.addView(stats);
    controls = new LinearLayout(this);
    runPage.addView(controls);
    primary =
        MobileUi.button(
            controls,
            "开始",
            () -> {
              connect(true);
              if (service == null) return;
              ReplayEngine.State s = service.replaySnapshot().state;
              if (s == ReplayEngine.State.RUNNING || s == ReplayEngine.State.PAUSED)
                service.pauseOrResumeReplay();
              else
                service.startReplay(
                    currentRoute(),
                    service.configuredMode == 0 ? (int) service.configuredTarget : 1,
                    service.configuredMode == 1 ? service.configuredTarget * 1000 : null,
                    service.configuredSpeed);
              render();
            });
    stop =
        MobileUi.button(
            controls,
            "停止",
            () -> {
              if (service != null) service.stopReplay();
              render();
            });
    MobileUi.button(controls, "参数", this::parameters);
    routeList = MobileUi.column(this);
    ScrollView routes = new ScrollView(this);
    routes.addView(routeList);
    routePage = routes;
    pages.addView(routes, new FrameLayout.LayoutParams(-1, -1));
    settingsPage = MobileUi.column(this);
    ScrollView settings = new ScrollView(this);
    settings.addView(settingsPage);
    settingsScroll = settings;
    pages.addView(settings, new FrameLayout.LayoutParams(-1, -1));
    tutorialPage = MobileUi.column(this);
    ScrollView tutorial = new ScrollView(this);
    tutorial.addView(tutorialPage);
    tutorialScroll = tutorial;
    pages.addView(tutorial, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout nav = new LinearLayout(this);
    root.addView(nav);
    String[] names = {"跑步", "路线", "设置", "教程"};
    int[] icons = {
      com.zcshou.gogogo.R.drawable.ic_run_outline,
      com.zcshou.gogogo.R.drawable.ic_route_outline,
      com.zcshou.gogogo.R.drawable.ic_settings_outline,
      com.zcshou.gogogo.R.drawable.ic_tutorial_outline
    };
    for (int i = 0; i < 4; i++) {
      final int index = i;
      LinearLayout item = new LinearLayout(this);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(android.view.Gravity.CENTER);
      item.setContentDescription(names[i]);
      ImageView icon = new ImageView(this);
      icon.setImageResource(icons[i]);
      item.addView(
          icon, new LinearLayout.LayoutParams(MobileUi.dp(this, 40), MobileUi.dp(this, 28)));
      TextView label = MobileUi.text(this, names[i], 12);
      label.setGravity(Gravity.CENTER);
      label.setPadding(0, MobileUi.dp(this, 3), 0, 0);
      item.addView(label, new LinearLayout.LayoutParams(-1, -2));
      nav.addView(item, new LinearLayout.LayoutParams(0, MobileUi.dp(this, 64), 1));
      navigation[i] = item;
      item.setOnClickListener(v -> showTab(index));
    }
    if (getIntent().getBooleanExtra("open_routes", false)) tab = 1;
    showTab(tab);
    connect(false);
  }

  private RouteStore.Route currentRoute() {
    return service != null ? service.selectedRoute : MobileUi.find(this, selectedId);
  }

  private void prepare() {
    service.appForeground(true);
    if (getIntent().hasExtra("selected_route_id")) {
      service.prepareSession(MobileUi.find(this, selectedId));
      getIntent().removeExtra("selected_route_id");
    } else if (com.zcshou.gogogo.GoApplication.campusFresh && !service.replayLocked()) {
      selectedId = -1;
      service.configuredSpeed = 16;
      service.configuredMode = 0;
      service.configuredTarget = 1;
      service.prepareSession(RouteStore.nanhu());
    }
    com.zcshou.gogogo.GoApplication.campusFresh = false;
    if (pendingConfig) {
      service.changeReplaySpeed(localSpeed);
      service.configuredMode = localMode;
      service.configuredTarget = localGoal;
      pendingConfig = false;
    }
  }

  private void render() {
    if (followTool != null) {
      followTool.setSelected(follow);
      followTool.setColorFilter(follow ? 0xff087f74 : 0xff657a75);
      followTool.setAlpha(follow ? 1f : 0.7f);
    }
    if (stats == null) return;
    RouteStore.Route r = currentRoute();
    if (pane.map != null && mappedId != r.id) {
      mappedId = r.id;
      pane.coordinateSystem = CoordinateSystem.WGS84;
      pane.draw(ReplayPanel.toWgs(r), false);
      pane.fit(ReplayPanel.toWgs(r));
    }
    if (service == null) {
      stats.setText(r.name + "\n16 km/h · 待授权，点击开始完成必要设置");
      primary.setText("开始");
      stop.setEnabled(false);
      return;
    }
    ReplayEngine.Snapshot s = service.replaySnapshot();
    boolean running = s.state == ReplayEngine.State.RUNNING;
    primary.setText(running ? "暂停" : s.state == ReplayEngine.State.PAUSED ? "继续" : "开始");
    MobileUi.style(primary, primary.getText().toString());
    stop.setEnabled(service.replayLocked());
    stats.setText(
        String.format(
            Locale.CHINA,
            "%s · %s\n%.3f / %.3f km · %.1f km/h\n有效用时 %02d:%02d",
            r.name,
            running ? "运行中" : s.state == ReplayEngine.State.PAUSED ? "已暂停" : "待开始",
            s.metres / 1000,
            s.target / 1000,
            running ? s.speedKmh : 0,
            s.activeMillis / 60000,
            s.activeMillis / 1000 % 60));
    pane.position(s.point, tab == 0 && follow && running);
  }

  private ImageButton mapTool(LinearLayout parent, String label, int image, Runnable action) {
    ImageButton b = new ImageButton(this);
    b.setImageResource(image);
    b.setColorFilter(MobileUi.GREEN);
    b.setContentDescription(label);
    b.setTooltipText(label);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xf5ffffff);
    bg.setCornerRadius(MobileUi.dp(this, 14));
    b.setBackground(bg);
    b.setElevation(MobileUi.dp(this, 2));
    b.setPadding(
        MobileUi.dp(this, 12), MobileUi.dp(this, 12), MobileUi.dp(this, 12), MobileUi.dp(this, 12));
    LinearLayout.LayoutParams lp =
        new LinearLayout.LayoutParams(MobileUi.dp(this, 48), MobileUi.dp(this, 48));
    lp.setMargins(0, 0, 0, MobileUi.dp(this, 8));
    parent.addView(b, lp);
    b.setOnClickListener(v -> {
      action.run();
      b.setAlpha(0.65f);
      b.animate().alpha(1f).setDuration(140).start();
    });
    return b;
  }

  private void showTab(int index) {
    index = Math.max(0, Math.min(3, index));
    tab = index;
    for (int i = 0; i < navigation.length; i++)
      if (navigation[i] != null) {
        LinearLayout item = (LinearLayout) navigation[i];
        boolean selected = i == index;
        item.setSelected(selected);
        android.graphics.drawable.GradientDrawable bg =
            new android.graphics.drawable.GradientDrawable();
        bg.setColor(selected ? 0xffd8eee8 : android.graphics.Color.TRANSPARENT);
        bg.setCornerRadius(MobileUi.dp(this, 16));
        item.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ((ImageView) item.getChildAt(0))
            .setPadding(
                MobileUi.dp(this, 8),
                MobileUi.dp(this, 2),
                MobileUi.dp(this, 8),
                MobileUi.dp(this, 2));
        ((ImageView) item.getChildAt(0)).setBackground(bg);
        ((ImageView) item.getChildAt(0)).setColorFilter(selected ? MobileUi.GREEN : 0xff74817e);
        TextView label = (TextView) item.getChildAt(1);
        label.setTextColor(selected ? MobileUi.GREEN : 0xff74817e);
        label.setTypeface(
            null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
      }

    runPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
    routePage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
    settingsScroll.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    tutorialScroll.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    if (index == 3) PermissionTutorial.show(this, tutorialPage);
    if (index == 1) MobileUi.routes(this, routeList);
    if (index == 2) settings();
  }

  private void settings() {
    settingsPage.removeAllViews();
    settingsPage.addView(MobileUi.text(this, "设置", 24));
    settingsPage.addView(MobileUi.text(this, "WP快跑 1.12.9\n地图可独立使用；开始任务时再完成定位和悬浮窗授权。", 16));
    android.content.SharedPreferences prefs = getSharedPreferences("guidance", 0);
    android.widget.Switch keep = new android.widget.Switch(this);
    keep.setText("运行期间保持亮屏（可见窗口）");
    keep.setChecked(prefs.getBoolean("keepAwake", true));
    settingsPage.addView(keep);
    keep.setOnCheckedChangeListener((v, checked) -> {
      prefs.edit().putBoolean("keepAwake", checked).apply();
      if (service != null) service.refreshKeepScreen();
    });
    MobileUi.button(settingsPage, "权限开启教程", () -> showTab(3));
    MobileUi.button(
        settingsPage,
        "通知设置",
        () ->
            startActivity(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
    MobileUi.button(
        settingsPage,
        "后台耗电设置",
        () -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
    MobileUi.button(
        settingsPage,
        "地图加载重试",
        () -> {
          showTab(0);
          pane.retry();
        });
    MobileUi.button(
        settingsPage,
        "腾讯地图隐私说明",
        () ->
            startActivity(
                new Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "https://lbs.qq.com/userAgreements/agreements/privacy"))));
    settingsPage.addView(
        MobileUi.text(
            this,
            "位置权限："
                + (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                    ? "已允许"
                    : "未允许")
                + "\n悬浮窗："
                + (Settings.canDrawOverlays(this) ? "已允许" : "未允许"),
            16));
  }

  private void parameters() {
    LinearLayout form = MobileUi.column(this);
    EditText speed = new EditText(this);
    speed.setInputType(8194);
    speed.setHint("速度 km/h");
    speed.setText("" + (service == null ? localSpeed : service.configuredSpeed));
    form.addView(speed);
    Spinner mode = new Spinner(this);
    mode.setAdapter(
        new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_dropdown_item, new String[] {"圈数", "距离 km"}));
    mode.setSelection(service == null ? localMode : service.configuredMode);
    form.addView(mode);
    EditText goal = new EditText(this);
    goal.setInputType(8194);
    goal.setHint("圈数 / km");
    goal.setText("" + (service == null ? localGoal : service.configuredTarget));
    form.addView(goal);
    mode.setEnabled(!MobileUi.locked());
    goal.setEnabled(!MobileUi.locked());
    final int[] oldMode = {service == null ? localMode : service.configuredMode};
    mode.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
            if (position != oldMode[0]) {
              goal.setText(position == 0 ? "1" : "3");
              oldMode[0] = position;
            }
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    new AlertDialog.Builder(this)
        .setTitle("任务参数")
        .setView(form)
        .setPositiveButton(
            "应用",
            (d, w) -> {
              try {
                double v = Double.parseDouble(speed.getText().toString()),
                    g = Double.parseDouble(goal.getText().toString());
                if (!Double.isFinite(v)
                    || v <= 0
                    || v > 100
                    || !Double.isFinite(g)
                    || g <= 0
                    || (mode.getSelectedItemPosition() == 0
                        && (g != Math.rint(g) || g > Integer.MAX_VALUE)))
                  throw new IllegalArgumentException("请输入有效速度和目标");
                localSpeed = v;
                localGoal = g;
                localMode = mode.getSelectedItemPosition();
                pendingConfig = service == null;
                if (service != null) {
                  service.changeReplaySpeed(v);
                  if (!service.replayLocked()) {
                    service.configuredMode = mode.getSelectedItemPosition();
                    service.configuredTarget = g;
                  }
                }
                render();
              } catch (RuntimeException e) {
                Toast.makeText(this, "参数无效", Toast.LENGTH_LONG).show();
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent.hasExtra("selected_route_id")) {
      selectedId = intent.getLongExtra("selected_route_id", -1);
      if (service != null && !service.replayLocked()) prepare();
      mappedId = Long.MIN_VALUE;
      showTab(0);
      render();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 77 || result != RESULT_OK || data == null) return;
    try {
      MobileUi.editable();
      try (java.io.InputStream input = getContentResolver().openInputStream(data.getData());
          java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) != -1) {
          if (bytes.size() + n > 2097152) throw new IllegalArgumentException("CSV超过2MB");
          bytes.write(buf, 0, n);
        }
        RouteCsv.Document doc = RouteCsv.parseDocument(bytes.toString("UTF-8"));
        EditText name = new EditText(this);
        name.setText("导入路线");
        new AlertDialog.Builder(this)
            .setTitle("保存导入路线")
            .setView(name)
            .setPositiveButton(
                "保存",
                (d, w) -> {
                  try {
                    MobileUi.editable();
                    RouteStore.Route r = new RouteStore.Route(name.getText().toString());
                    r.coordinateSystem = doc.coordinateSystem;
                    r.points.addAll(doc.points);
                    try (RouteStore store = new RouteStore(this)) {
                      store.save(r);
                    }
                    MobileUi.detail(this, r.id);
                  } catch (RuntimeException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                  }
                })
            .setNegativeButton("取消", null)
            .show();
      }
    } catch (Exception e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void connect(boolean ask) {
    if (!Settings.canDrawOverlays(this)) {
      if (ask) showTab(3);
      return;
    }
    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      if (ask)
        requestPermissions(
            new String[] {
              android.Manifest.permission.ACCESS_FINE_LOCATION,
              android.Manifest.permission.ACCESS_COARSE_LOCATION
            },
            12);
      return;
    }
    AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
    if (ops.checkOpNoThrow(
            AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), getPackageName())
        != AppOpsManager.MODE_ALLOWED) {
      if (ask) showTab(3);
      return;
    }
    if (!ServiceGo.isAlive()) startForegroundService(new Intent(this, ServiceGo.class));
    if (!bound) bound = bindService(new Intent(this, ServiceGo.class), connection, 0);
    else if (service != null) {
      prepare();
      render();
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
    super.onRequestPermissionsResult(request, permissions, result);
    if (tab == 3) PermissionTutorial.show(this, tutorialPage);
    if (tab == 2) settings();
    connect(false);
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (pane != null) pane.start();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (controls != null) connect(false);
    if (pane != null) pane.resume();
    if (tab == 1) MobileUi.routes(this, routeList);
    if (tab == 2) settings();
    if (tab == 3) PermissionTutorial.show(this, tutorialPage);
    mappedId = Long.MIN_VALUE;
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    if (pane != null) pane.pause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    if (pane != null) pane.stop();
    super.onStop();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putLong("selected", selectedId);
    out.putInt("tab", tab);
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (pane != null) pane.destroy();
    if (bound) unbindService(connection);
    super.onDestroy();
  }
}
