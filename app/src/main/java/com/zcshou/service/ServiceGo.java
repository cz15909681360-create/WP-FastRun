package com.zcshou.service;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.elvishew.xlog.XLog;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.R;
import com.zcshou.joystick.JoyStick;

public class ServiceGo extends Service {
  private static volatile ServiceGo live;
  private final com.zcshou.replay.CompletionGate completionGate =
      new com.zcshou.replay.CompletionGate();
  private com.zcshou.replay.CompletionNotice completionNotice;
  private String guidanceStatus = "";
  private androidx.core.app.NotificationCompat.Builder statusNotification;
  private final Handler stateUi = new Handler(android.os.Looper.getMainLooper());

  public boolean keepAwakeEnabled() {
    return getSharedPreferences("guidance", 0).getBoolean("keepAwake", true);
  }

  public void setGuidanceStatus(String text, boolean hidden) {
    guidanceStatus = text;
    refreshKeepScreen();
    String status =
        text
            + " · "
            + (keepAwakeEnabled()
                    && replaySnapshot().state == com.zcshou.replay.ReplayEngine.State.RUNNING
                    && !hidden
                ? "常亮已启用（窗口可见时）"
                : "常亮未启用");
    if (!status.equals(lastNotificationStatus) && statusNotification != null) {
      lastNotificationStatus = status;
      ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE))
          .notify(
              SERVICE_GO_NOTE_ID,
              statusNotification.setContentText(status).setOnlyAlertOnce(true).build());
    }
  }

  private String lastNotificationStatus = "";

  public void refreshKeepScreen() {
    boolean enabled =
        keepAwakeEnabled()
            && replaySnapshot().state == com.zcshou.replay.ReplayEngine.State.RUNNING;
    android.app.Activity a = com.zcshou.gogogo.GoApplication.currentActivity.get();
    if (a != null) {
      if (enabled)
        a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      else a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    android.view.View[] windows = {offlineEntry, replayWindow};
    for (android.view.View view : windows)
      if (view != null && view.getParent() != null) {
        android.view.WindowManager.LayoutParams p =
            (android.view.WindowManager.LayoutParams) view.getLayoutParams();
        int flags =
            enabled
                ? p.flags | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                : p.flags & ~android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (flags != p.flags) {
          p.flags = flags;
          ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(view, p);
        }
      }
  }

  public static ServiceGo current() {
    return live;
  }

  public com.zcshou.replay.RouteStore.Route selectedRoute = com.zcshou.replay.RouteStore.nanhu();
  public double configuredSpeed = 16, configuredTarget = 1;
  public int configuredMode = 0;
  private final Handler overlayHandler = new Handler(android.os.Looper.getMainLooper());
  private boolean foreground = true;

  public synchronized void prepareSession(com.zcshou.replay.RouteStore.Route route) {
    if (replay.locked()) return;

    selectedRoute = route;
    routeName = route.name;
    replay.prepare(com.zcshou.replay.ReplayPanel.toWgs(route), configuredSpeed);
    com.zcshou.replay.ReplayEngine.Point p = replay.snapshot().point;
    mCurLat = p.lat;
    mCurLng = p.lon;
    mSpeed = 0;
    replayWasActive = false;
  }

  public void appForeground(boolean value) {
    foreground = value;

    if (value) {
      hideCampusWindows();
    } else {
      showOfflineEntry();
    }
    refreshKeepScreen();
  }

  private void hideCampusWindows() {
    overlayHandler.removeCallbacksAndMessages(null);
    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    if (offlineEntry != null && offlineEntry.getParent() != null) wm.removeView(offlineEntry);
    if (replayWindow != null) {
      wm.removeView(replayWindow);
      replayWindow = null;
    }
  }

  private final Runnable compactRefresh =
      new Runnable() {
        public void run() {
          if (offlineEntry == null || offlineEntry.getParent() == null) return;
          com.zcshou.replay.ReplayEngine.Snapshot s = replay.snapshot();
          String state =
              s.state == com.zcshou.replay.ReplayEngine.State.RUNNING
                  ? "运行中"
                  : s.state == com.zcshou.replay.ReplayEngine.State.PAUSED
                      ? "已暂停"
                      : s.state == com.zcshou.replay.ReplayEngine.State.COMPLETED ? "已完成" : "待开始";
          double actual = s.state == com.zcshou.replay.ReplayEngine.State.RUNNING ? s.speedKmh : 0;
          double goal =
              configuredMode == 0 ? s.lapLength * configuredTarget : configuredTarget * 1000;
          double remaining = Math.max(0, goal - s.metres);
          offlineEntry.setText(
              String.format(
                  java.util.Locale.CHINA,
                  "%s · %s\n%.3f km · %.1f km/h（设定 %.1f）\n%02d:%02d · 剩余 %.3f km",
                  routeName,
                  state,
                  s.metres / 1000,
                  actual,
                  configuredSpeed,
                  s.activeMillis / 60000,
                  s.activeMillis / 1000 % 60,
                  remaining / 1000));
          overlayHandler.postDelayed(this, 500);
        }
      };

  public static boolean isAlive() {
    return live != null;
  }

  private final com.zcshou.replay.ReplayEngine replay = new com.zcshou.replay.ReplayEngine();
  private volatile String routeName = "";
  private boolean persistedActive;
  private boolean replayWasActive;
  private boolean replayUiLocked;
  private com.zcshou.replay.ReplayEngine.State lastUiState;
  private android.os.PowerManager.WakeLock replayWake;
  private android.view.View replayWindow;
  private android.widget.Button offlineEntry;

  public boolean replayLocked() {
    return replay.locked();
  }

  public String replayRouteName() {
    return routeName;
  }

  public com.zcshou.replay.ReplayEngine.Snapshot replaySnapshot() {
    return replay.snapshot();
  }

  public synchronized void startReplay(
      com.zcshou.replay.RouteStore.Route route, int laps, Double metres, double speed) {
    replay.start(
        com.zcshou.replay.ReplayPanel.toWgs(route),
        laps,
        metres,
        speed,
        SystemClock.elapsedRealtime());
    completionGate.begin();
    if (completionNotice != null) stateUi.post(completionNotice::clear);
    selectedRoute = route;
    configuredSpeed = speed;
    configuredMode = metres == null ? 0 : 1;
    configuredTarget = metres == null ? laps : metres / 1000;
    routeName = route.name;
    persistedActive = true;
    getSharedPreferences("replay", 0).edit().putBoolean("active", true).apply();
    updateReplay();
  }

  public synchronized void pauseOrResumeReplay() {
    if (replay.snapshot().state == com.zcshou.replay.ReplayEngine.State.RUNNING)
      replay.pause(SystemClock.elapsedRealtime());
    else replay.resume(SystemClock.elapsedRealtime());
    updateReplay();
  }

  public synchronized void changeReplaySpeed(double value) {
    replay.speed(value, SystemClock.elapsedRealtime());
    configuredSpeed = value;
    updateReplay();
  }

  public synchronized void stopReplay() {
    replay.stop(SystemClock.elapsedRealtime());
    updateReplay();
  }

  private synchronized void updateReplay() {
    replay.tick(SystemClock.elapsedRealtime());
    com.zcshou.replay.ReplayEngine.Snapshot s = replay.snapshot();
    long token = completionGate.observe(s.state);
    if (token >= 0)
      stateUi.post(() -> {
        if (!isStop && completionGate.valid(token) && completionNotice != null)
          completionNotice.show();
      });
    if (s.point != null && (replay.locked() || replayWasActive)) {
      mCurLat = s.point.lat;
      mCurLng = s.point.lon;
      mCurBea = (float) s.bearing;
      mSpeed = s.state == com.zcshou.replay.ReplayEngine.State.RUNNING ? s.speedKmh / 3.6 : 0;
    }
    replayWasActive = replay.locked();
    if (replayUiLocked != replayWasActive) {
      replayUiLocked = replayWasActive;
      boolean locked = replayUiLocked;
      new Handler(getMainLooper())
          .post(() -> {
            if (!isStop && mJoyStick != null) mJoyStick.setReplayLocked(locked);
          });
    }
    boolean running = s.state == com.zcshou.replay.ReplayEngine.State.RUNNING;
    if (lastUiState != s.state) {
      lastUiState = s.state;
      stateUi.post(() -> {
        if (!isStop)
          setGuidanceStatus(
              replaySnapshot().state == com.zcshou.replay.ReplayEngine.State.RUNNING
                  ? "运行中"
                  : replaySnapshot().state == com.zcshou.replay.ReplayEngine.State.COMPLETED
                      ? "已完成"
                      : "待开始 / 已暂停",
              false);
      });
    }
    if (replayWake != null) {
      if (running && !replayWake.isHeld()) replayWake.acquire(10 * 60 * 1000L);
      else if (!running && replayWake.isHeld()) replayWake.release();
    }
    if (persistedActive != replay.locked()) {
      persistedActive = replay.locked();
      getSharedPreferences("replay", 0).edit().putBoolean("active", persistedActive).apply();
    }
  }

  private void showOfflineEntry() {
    if (foreground || !android.provider.Settings.canDrawOverlays(this)) return;
    if (offlineEntry != null && offlineEntry.getParent() != null) return;
    offlineEntry = new android.widget.Button(this);
    offlineEntry.setText("WP快跑");
    offlineEntry.setTextColor(android.graphics.Color.WHITE);
    offlineEntry.setTextSize(12);
    offlineEntry.setContentDescription("定位任务悬浮卡片，点击展开");
    offlineEntry.setAllCaps(false);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xb319232f);
    bg.setCornerRadius(20);
    offlineEntry.setBackground(bg);
    offlineEntry.setPadding(24, 12, 24, 12);
    offlineEntry.setOnClickListener(v -> showReplayPanel());
    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    android.view.WindowManager.LayoutParams p =
        new android.view.WindowManager.LayoutParams(
            -2,
            -2,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT);
    p.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
    p.x = 20;
    p.y = 200;
    offlineEntry.setOnTouchListener(
        new android.view.View.OnTouchListener() {
          float x, y;
          int px, py;
          boolean moved;

          public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
            if (e.getAction() == 0) {
              x = e.getRawX();
              y = e.getRawY();
              px = p.x;
              py = p.y;
              moved = false;
            }
            if (e.getAction() == 2
                && (Math.abs(e.getRawX() - x) > 8 || Math.abs(e.getRawY() - y) > 8)) {
              moved = true;
              p.x = px + (int) (e.getRawX() - x);
              p.y = py + (int) (e.getRawY() - y);
              wm.updateViewLayout(v, p);
              return true;
            }
            return e.getAction() == 1 && moved;
          }
        });
    wm.addView(offlineEntry, p);
    refreshKeepScreen();
    overlayHandler.removeCallbacks(compactRefresh);
    overlayHandler.post(compactRefresh);
  }

  public void showReplayPanel() {
    if (foreground || !android.provider.Settings.canDrawOverlays(this)) return;
    if (replayWindow != null) return;
    hideCampusWindows();
    android.widget.LinearLayout content = new android.widget.LinearLayout(this);
    content.setOrientation(android.widget.LinearLayout.VERTICAL);
    content.setBackgroundColor(0xfff5f5f5);
    android.widget.Button close = new android.widget.Button(this);
    close.setText("收起轨迹面板");
    content.addView(close);
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    scroll.addView(new com.zcshou.replay.ReplayPanel(this, this));
    content.addView(scroll);
    android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    android.view.WindowManager.LayoutParams params =
        new android.view.WindowManager.LayoutParams(
            (int) (300 * getResources().getDisplayMetrics().density),
            (int) (480 * getResources().getDisplayMetrics().density),
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT);
    params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
    params.x = 20;
    params.y = 100;
    params.softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    close.setOnClickListener(v -> {
      wm.removeView(content);
      replayWindow = null;
      if (!foreground) showOfflineEntry();
    });
    close.setOnTouchListener(
        new android.view.View.OnTouchListener() {
          float x, y;
          int px, py;

          public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
            if (e.getAction() == 0) {
              x = e.getRawX();
              y = e.getRawY();
              px = params.x;
              py = params.y;
            }
            if (e.getAction() == 2) {
              params.x = px + (int) (e.getRawX() - x);
              params.y = py + (int) (e.getRawY() - y);
              wm.updateViewLayout(content, params);
              return true;
            }
            return false;
          }
        });
    wm.addView(content, params);
    replayWindow = content;
    refreshKeepScreen();
  }

  // 定位相关变量
  private static final com.zcshou.replay.ReplayEngine.Point CAMPUS_START =
      com.zcshou.replay.MapCoordinates.convert(
          com.zcshou.replay.RouteStore.nanhu().points.get(0),
          com.zcshou.replay.CoordinateSystem.GCJ02,
          com.zcshou.replay.CoordinateSystem.WGS84);
  public static final double DEFAULT_LAT = CAMPUS_START.lat;
  public static final double DEFAULT_LNG = CAMPUS_START.lon;
  public static final double DEFAULT_ALT = 55.0D;
  public static final float DEFAULT_BEA = 0.0F;
  private double mCurLat = DEFAULT_LAT;
  private double mCurLng = DEFAULT_LNG;
  private double mCurAlt = DEFAULT_ALT;
  private float mCurBea = DEFAULT_BEA;
  private double mSpeed = 0; /* 默认的速度，单位 m/s */
  private static final int HANDLER_MSG_ID = 0;
  private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
  private LocationManager mLocManager;
  private HandlerThread mLocHandlerThread;
  private Handler mLocHandler;
  private boolean isStop = false;
  // 通知栏消息
  private static final int SERVICE_GO_NOTE_ID = 1;
  private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
  private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
  private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
  private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE";
  private NoteActionReceiver mActReceiver;
  // 摇杆相关
  private JoyStick mJoyStick;

  private final ServiceGoBinder mBinder = new ServiceGoBinder();

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    live = this;
    prepareSession(com.zcshou.replay.RouteStore.nanhu());
    replayWake =
        ((android.os.PowerManager) getSystemService(POWER_SERVICE))
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "YinSuo:replay");
    replayWake.setReferenceCounted(false);

    mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

    removeTestProviderNetwork();
    addTestProviderNetwork();

    removeTestProviderGPS();
    addTestProviderGPS();

    initGoLocation();

    initNotification();

    if (com.zcshou.gogogo.GoApplication.mapsEnabled()) initJoyStick();
    else if (!foreground) showOfflineEntry();
    completionNotice = new com.zcshou.replay.CompletionNotice(this);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    if ("STOP_ALL".equals(intent.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (replay.locked()) return START_NOT_STICKY;
    if (live == this && !intent.hasExtra(MainActivity.LNG_MSG_ID)) return START_NOT_STICKY;
    mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
    mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
    mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);

    if (mJoyStick != null) mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    isStop = true;
    completionGate.invalidate();
    stateUi.removeCallbacksAndMessages(null);
    if (completionNotice != null) completionNotice.clear();
    android.app.Activity active = com.zcshou.gogogo.GoApplication.currentActivity.get();
    if (active != null)
      active.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    overlayHandler.removeCallbacksAndMessages(null);
    live = null;
    if (replayWake != null && replayWake.isHeld()) replayWake.release();
    if (replayWindow != null)
      ((android.view.WindowManager) getSystemService(WINDOW_SERVICE))
          .removeViewImmediate(replayWindow);
    getSharedPreferences("replay", 0).edit().putBoolean("active", false).apply();
    mLocHandler.removeMessages(HANDLER_MSG_ID);
    mLocHandlerThread.quit();

    if (mJoyStick != null) mJoyStick.destroy();
    if (offlineEntry != null && offlineEntry.getParent() != null)
      ((android.view.WindowManager) getSystemService(WINDOW_SERVICE))
          .removeViewImmediate(offlineEntry);

    removeTestProviderNetwork();
    removeTestProviderGPS();

    unregisterReceiver(mActReceiver);
    stopForeground(STOP_FOREGROUND_REMOVE);

    super.onDestroy();
  }

  private void failReplay() {
    if (replay.locked()) {
      stopReplay();
      stopSelf();
      new Handler(getMainLooper())
          .post(() ->
              android.widget.Toast.makeText(
                      this, "模拟定位失败，请检查开发者选项及位置权限", android.widget.Toast.LENGTH_LONG)
                  .show());
    }
  }

  private void initNotification() {
    mActReceiver = new NoteActionReceiver();
    IntentFilter filter = new IntentFilter();
    filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
    filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(mActReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(mActReceiver, filter);

    NotificationChannel mChannel =
        new NotificationChannel(
            SERVICE_GO_NOTE_CHANNEL_ID,
            SERVICE_GO_NOTE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT);
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

    if (notificationManager != null) {
      notificationManager.createNotificationChannel(mChannel);
    }

    // 准备intent
    Intent clickIntent =
        new Intent(
            this,
            com.zcshou.gogogo.GoApplication.mapsEnabled()
                ? MainActivity.class
                : com.zcshou.replay.CampusActivity.class);
    PendingIntent clickPI =
        PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
    Intent showIntent =
        new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW).setPackage(getPackageName());
    PendingIntent showPendingPI =
        PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
    Intent hideIntent =
        new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE).setPackage(getPackageName());
    PendingIntent hidePendingPI =
        PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

    Intent stopIntent = new Intent(this, ServiceGo.class).setAction("STOP_ALL");
    PendingIntent stopPI =
        PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE);
    statusNotification =
        new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
            .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
            .setContentTitle(getResources().getString(R.string.app_name))
            .setContentText(getResources().getString(R.string.app_service_tips))
            .setContentIntent(clickPI)
            .addAction(
                new NotificationCompat.Action(
                    null, getResources().getString(R.string.note_show), showPendingPI))
            .addAction(new NotificationCompat.Action(null, "收起", hidePendingPI))
            .addAction(new NotificationCompat.Action(null, "停止定位", stopPI))
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_follow_outline);

    startForeground(SERVICE_GO_NOTE_ID, statusNotification.build());
  }

  private void initJoyStick() {
    mJoyStick = new JoyStick(this);
    mJoyStick.setListener(
        new JoyStick.JoyStickClickListener() {
          @Override
          public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
            if (replay.locked()) return;
            mSpeed = speed;
            // 根据当前的经纬度和距离，计算下一个经纬度
            // Latitude: 1 deg = 110.574 km // 纬度的每度的距离大约为 110.574km
            // Longitude: 1 deg = 111.320*cos(latitude) km  // 经度的每度的距离从0km到111km不等
            // 具体见：http://wp.mlab.tw/?p=2200
            mCurLng += disLng / (111.320 * Math.cos(Math.abs(mCurLat) * Math.PI / 180));
            mCurLat += disLat / 110.574;
            mCurBea = (float) angle;
          }

          @Override
          public void onPositionInfo(double lng, double lat, double alt) {
            if (replay.locked()) return;
            mCurLng = lng;
            mCurLat = lat;
            mCurAlt = alt;
          }
        });
    mJoyStick.show();
  }

  private void initGoLocation() {
    // 创建 HandlerThread 实例，第一个参数是线程的名字
    mLocHandlerThread =
        new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
    // 启动 HandlerThread 线程
    mLocHandlerThread.start();
    // Handler 对象与 HandlerThread 的 Looper 对象的绑定
    mLocHandler =
        new Handler(mLocHandlerThread.getLooper()) {
          // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
          @Override
          public void handleMessage(@NonNull Message msg) {
            try {
              Thread.sleep(100);

              if (!isStop) {
                updateReplay();
                setLocationNetwork();
                setLocationGPS();

                sendEmptyMessage(HANDLER_MSG_ID);
              }
            } catch (InterruptedException e) {
              XLog.e("SERVICEGO: ERROR - handleMessage");
              Thread.currentThread().interrupt();
            }
          }
        };

    mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
  }

  private void removeTestProviderGPS() {
    try {
      if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
        mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
      }
    } catch (Exception e) {
      XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
    }
  }

  // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
  @SuppressLint("wrongconstant")
  private void addTestProviderGPS() {
    try {
      // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        mLocManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            true,
            false,
            false,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_HIGH,
            ProviderProperties.ACCURACY_FINE);
      } else {
        mLocManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            true,
            false,
            false,
            true,
            true,
            true,
            Criteria.POWER_HIGH,
            Criteria.ACCURACY_FINE);
      }
      if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
      }
    } catch (Exception e) {
      XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
    }
  }

  private void setLocationGPS() {
    try {
      // 尽可能模拟真实的 GPS 数据
      Location loc = new Location(LocationManager.GPS_PROVIDER);
      loc.setAccuracy(Criteria.ACCURACY_FINE); // 设定此位置的估计水平精度，以米为单位。
      loc.setAltitude(mCurAlt); // 设置高度，在 WGS 84 参考坐标系中的米
      loc.setBearing(mCurBea); // 方向（度）
      loc.setLatitude(mCurLat); // 纬度（度）
      loc.setLongitude(mCurLng); // 经度（度）
      loc.setTime(System.currentTimeMillis()); // 本地时间
      loc.setSpeed((float) mSpeed);
      loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
      Bundle bundle = new Bundle();
      bundle.putInt("satellites", 7);
      loc.setExtras(bundle);

      mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
    } catch (Exception e) {
      XLog.e("SERVICEGO: ERROR - setLocationGPS", e);
      failReplay();
    }
  }

  private void removeTestProviderNetwork() {
    try {
      if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
      }
    } catch (Exception e) {
      XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
    }
  }

  // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
  @SuppressLint("wrongconstant")
  private void addTestProviderNetwork() {
    try {
      // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        mLocManager.addTestProvider(
            LocationManager.NETWORK_PROVIDER,
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            ProviderProperties.POWER_USAGE_LOW,
            ProviderProperties.ACCURACY_COARSE);
      } else {
        mLocManager.addTestProvider(
            LocationManager.NETWORK_PROVIDER,
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_COARSE);
      }
      if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
        mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
      }
    } catch (SecurityException e) {
      XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
    }
  }

  private void setLocationNetwork() {
    try {
      // 尽可能模拟真实的 NETWORK 数据
      Location loc = new Location(LocationManager.NETWORK_PROVIDER);
      loc.setAccuracy(Criteria.ACCURACY_COARSE); // 设定此位置的估计水平精度，以米为单位。
      loc.setAltitude(mCurAlt); // 设置高度，在 WGS 84 参考坐标系中的米
      loc.setBearing(mCurBea); // 方向（度）
      loc.setLatitude(mCurLat); // 纬度（度）
      loc.setLongitude(mCurLng); // 经度（度）
      loc.setTime(System.currentTimeMillis()); // 本地时间
      loc.setSpeed((float) mSpeed);
      loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

      mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
    } catch (Exception e) {
      XLog.e("SERVICEGO: ERROR - setLocationNetwork", e);
      failReplay();
    }
  }

  public class NoteActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action != null) {
        if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
          if (mJoyStick != null) mJoyStick.show();
          else showOfflineEntry();
        }

        if (action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
          hideCampusWindows();
          if (!foreground) showOfflineEntry();
          refreshKeepScreen();
        }
      }
    }
  }

  public class ServiceGoBinder extends Binder {
    public ServiceGo getService() {
      return ServiceGo.this;
    }

    public void setPosition(double lng, double lat, double alt) {
      if (replay.locked()) return;
      mLocHandler.removeMessages(HANDLER_MSG_ID);
      mCurLng = lng;
      mCurLat = lat;
      mCurAlt = alt;
      mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
      if (mJoyStick != null) mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
    }
  }
}
