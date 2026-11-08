package com.zcshou.replay;

import android.app.Activity;
import android.os.*;
import android.widget.*;
import com.tencent.tencentmap.mapsdk.maps.*;
import com.tencent.tencentmap.mapsdk.maps.model.*;
import java.util.*;

/** Creates the SDK only after explicit first-use agreement and a configured Key. */
public final class TencentMapPane extends LinearLayout {
  public CoordinateSystem coordinateSystem = CoordinateSystem.WGS84;
  public MapView view;
  public TencentMap map;
  private final Activity activity;
  private final TextView message;
  private Runnable ready;
  private final android.os.Handler statusHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean loaded, destroyed, started, resumed;
  private Button agreeButton, policyButton;
  private final List<com.tencent.tencentmap.mapsdk.maps.model.Marker> markers = new ArrayList<>();
  private Polyline line;
  private java.util.List<ReplayEngine.Point> lastFit;
  private Marker position;

  public TencentMapPane(Activity a, Runnable ready) {
    super(a);
    activity = a;
    this.ready = ready;
    setOrientation(VERTICAL);
    message = new TextView(a);
    message.setText("腾讯地图将通过网络加载地图，并处理地图交互所需的设备和网络信息。暂不启用地图仍可复演已有路线。");
    addView(message);
    Button agree = new Button(a);
    agreeButton = agree;
    agree.setText("同意并加载地图 / 重试");
    addView(agree);
    agree.setOnClickListener(v -> {
      a.getSharedPreferences("tencent_map", 0).edit().putBoolean("agreed", true).apply();
      load();
    });
    Button policy = new Button(a);
    policyButton = policy;
    policy.setText("查看腾讯位置服务隐私说明");
    addView(policy);
    policy.setOnClickListener(v ->
        a.startActivity(
            new android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://lbs.qq.com/userAgreements/agreements/privacy"))));
    if (a.getSharedPreferences("tencent_map", 0).getBoolean("agreed", false)) post(this::load);
  }

  private void load() {
    if (destroyed || activity.isFinishing()) return;
    if (map != null) {
      statusHandler.removeCallbacksAndMessages(null);
      view.onPause();
      view.onStop();
      view.onDestroy();
      removeView(view);
      view = null;
      map = null;
      markers.clear();
      line = null;
      position = null;
    }
    try {
      android.content.pm.ApplicationInfo info =
          activity
              .getPackageManager()
              .getApplicationInfo(
                  activity.getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
      String key = info.metaData.getString("TencentMapSDK", "");
      if (key.trim().isEmpty()) {
        message.setText("尚未配置腾讯地图 Key。已有路线仍可复演；配置后可重试。");
        return;
      }
      TencentMapInitializer.setAgreePrivacy(activity, true);
      TencentMapInitializer.start(activity);
      view = new MapView(activity);
      if (started) view.onStart();
      map = view.getMap();
      loaded = false;
      map.setOnMapLoadedCallback(() -> {
        loaded = true;
        if(lastFit!=null)fit(lastFit);
        message.setText("腾讯地图已加载");
        message.setVisibility(GONE);
      });
      statusHandler.postDelayed(
          () -> {
            if (!loaded && map != null) message.setText("地图加载暂未确认。可检查网络或在设置中重试，已有任务不受影响。");
          },
          20000);
      addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
      if (resumed) view.onResume();
      agreeButton.setVisibility(GONE);
      policyButton.setVisibility(GONE);
      message.setText("腾讯地图 · 若地图空白，请检查网络及 Key 的地图SDK授权、包名绑定后重试。");
      if (ready != null) ready.run();
    } catch (Exception | LinkageError e) {
      android.util.Log.e("CampusMap", "Initialization failed: " + e.getClass().getSimpleName());
      agreeButton.setVisibility(VISIBLE);
      message.setText("地图加载失败，已有路线仍可使用。请检查 Key 和网络后重试。");
    }
  }

  public LatLng coordinateNative(ReplayEngine.Point p) {
    ReplayEngine.Point q = MapCoordinates.convert(p, coordinateSystem, CoordinateSystem.GCJ02);
    return new LatLng(q.lat, q.lon);
  }

  public void draw(List<ReplayEngine.Point> points, boolean draggable) {
    if (map == null) return;
    for (Marker m : markers) m.remove();
    markers.clear();
    if (line != null) line.remove();
    List<LatLng> latlng = new ArrayList<>();
    for (ReplayEngine.Point p : points) latlng.add(coordinateNative(p));
    if (latlng.size() > 1)
      line = map.addPolyline(new PolylineOptions().addAll(latlng).color(0xff1565c0).width(8));
    int count = points.size();
    if (count > 1 && ReplayEngine.metres(points.get(0), points.get(count - 1)) < 0.01) count--;
    for (int i = 0; i < count; i++) {
      Marker marker =
          map.addMarker(
              new MarkerOptions()
                  .position(latlng.get(i))
                  .title("路线点 " + (i + 1))
                  .draggable(draggable));
      marker.setTag(i);
      markers.add(marker);
    }
  }

  public void fit(List<ReplayEngine.Point> points) {
    lastFit=new java.util.ArrayList<>(points);
    if (map == null || points.isEmpty()) return;
    if (points.size() == 1) {
      map.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinateNative(points.get(0)), 17));
      return;
    }
    LatLngBounds.Builder b = new LatLngBounds.Builder();
    for (ReplayEngine.Point p : points) b.include(coordinateNative(p));
    view.post(() -> {
      try {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 45));
      } catch (RuntimeException ignored) {
      }
    });
  }

  public void position(ReplayEngine.Point p, boolean follow) {
    if (map == null || p == null) return;
    LatLng ll = coordinateNative(p);
    if (position == null)
      position = map.addMarker(new MarkerOptions().position(ll).title("复演当前位置"));
    else position.setPosition(ll);
    if (follow) map.moveCamera(CameraUpdateFactory.newLatLng(ll));
  }

  public void start() {
    started = true;
    if (view != null) view.onStart();
  }

  public void resume() {
    resumed = true;
    if (view != null) view.onResume();
  }

  public void pause() {
    resumed = false;
    if (view != null) view.onPause();
  }

  public void stop() {
    started = false;
    if (view != null) view.onStop();
  }

  public void retry() {
    message.setVisibility(VISIBLE);
    agreeButton.setVisibility(VISIBLE);
    load();
  }

  public void highlight(int selected) {
    for (int i = 0; i < markers.size(); i++) {
      Marker m = markers.get(i);
      m.setAlpha(i == selected ? 1f : 0.65f);
      m.setZIndex(i == selected ? 10 : 0);
      if (i == selected) m.showInfoWindow();
      else m.hideInfoWindow();
    }
  }

  public void destroy() {
    destroyed = true;
    statusHandler.removeCallbacksAndMessages(null);
    ready = null;
    if (view != null) {
      if (resumed) view.onPause();
      if (started) view.onStop();
      view.onDestroy();
    }
    map = null;
  }
}
