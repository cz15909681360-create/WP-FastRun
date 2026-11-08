package com.zcshou.replay;

import android.app.*;
import android.os.*;
import android.widget.*;
import com.tencent.tencentmap.mapsdk.maps.*;
import com.tencent.tencentmap.mapsdk.maps.model.*;
import java.util.*;

public final class MapEditorActivity extends Activity {
  private RouteDraft draft;
  private RouteStore store;
  private RouteStore.Route route;
  private TencentMapPane pane;
  private EditText name;
  private TextView summary;
  private CheckBox closed;
  private int selected = -1;
  private String baseline;
  private Button undoButton, redoButton;
  private LinearLayout tools;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    MobileUi.window(this);
    setTitle("地图编辑路线");
    store = new RouteStore(this);
    route =
        new RouteStore.Route(
            getIntent().getStringExtra("name") == null
                ? "新路线"
                : getIntent().getStringExtra("name"));
    route.coordinateSystem =
        CoordinateSystem.valueOf(
            getIntent().getStringExtra("coordinateSystem") == null
                ? "GCJ02"
                : getIntent().getStringExtra("coordinateSystem"));
    route.id = getIntent().getLongExtra("id", 0);
    if (route.id < 0) {
      route.id = 0;
      route.name += " 副本";
    }
    double[] raw =
        state == null ? getIntent().getDoubleArrayExtra("points") : state.getDoubleArray("points");
    List<ReplayEngine.Point> p = new ArrayList<>();
    if (raw != null)
      for (int i = 0; i < raw.length; i += 2) p.add(new ReplayEngine.Point(raw[i], raw[i + 1]));
    draft =
        getLastNonConfigurationInstance() instanceof RouteDraft
            ? (RouteDraft) getLastNonConfigurationInstance()
            : new RouteDraft(p);
    if (state == null && raw == null) draft=RouteDraft.fresh();
    if (state != null) {
      route.id = state.getLong("id");
      route.coordinateSystem =
          CoordinateSystem.valueOf(
              state.getString("coordinateSystem", route.coordinateSystem.name()));
      route.name = state.getString("name", route.name);
      draft.closed(state.getBoolean("closed"));
    }
    LinearLayout root = MobileUi.column(this);
    setContentView(root);
    LinearLayout toolbar = new LinearLayout(this);
    root.addView(toolbar);
    MobileUi.button(toolbar, "返回", this::onBackPressed);
    undoButton =
        MobileUi.button(
            toolbar,
            "撤销",
            () -> {
              draft.undo();
              selected = -1;
              redraw();
            });
    redoButton =
        MobileUi.button(
            toolbar,
            "重做",
            () -> {
              draft.redo();
              selected = -1;
              redraw();
            });
    MobileUi.button(toolbar, "保存", this::askName);
    name = new EditText(this);
    name.setText(route.name);
    summary = MobileUi.text(this, "", 14);
    root.addView(summary);
    LinearLayout campus = new LinearLayout(this);
    root.addView(campus);
    MobileUi.button(campus, "南湖", () -> jumpCampus(RouteStore.nanhu()));
    MobileUi.button(campus, "余家头", () -> jumpCampus(RouteStore.yujia()));
    closed = new CheckBox(this);
    closed.setText("闭合");
    closed.setChecked(draft.closed());
    campus.addView(closed);
    closed.setOnClickListener(v -> {
      if (editable()) {
        draft.closed(closed.isChecked());
        redraw();
      } else closed.setChecked(draft.closed());
    });
    android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
    root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
    pane = new TencentMapPane(this, this::mapReady);
    pane.coordinateSystem = route.coordinateSystem;
    frame.addView(pane, new android.widget.FrameLayout.LayoutParams(-1, -1));
    TextView cross = MobileUi.text(this, "＋", 30);
    cross.setTextColor(0xffe65100);
    android.widget.FrameLayout.LayoutParams crossParams =
        new android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER);
    frame.addView(cross, crossParams);
    tools = MobileUi.column(this);
    tools.setVisibility(android.view.View.GONE);
    root.addView(tools);
    MobileUi.button(
        tools,
        "删除选中点",
        () -> {
          if (editable() && selected >= 0 && selected < draft.vertices().size()) {
            draft.delete(selected);
            selected = -1;
            redraw();
          }
        });
    MobileUi.button(tools, "精确修改选中点", this::coordinates);
    MobileUi.button(tools, "预览全部路线", () -> pane.fit(draft.route()));
    LinearLayout actions = new LinearLayout(this);
    root.addView(actions);
    MobileUi.button(
        actions,
        "＋ 添加中心点",
        () -> {
          if (editable() && pane.map != null) {
            draft.add(from(pane.map.getCameraPosition().target));
            redraw();
          }
        });
    MobileUi.button(
        actions,
        "编辑工具",
        () ->
            tools.setVisibility(
                tools.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE
                    : android.view.View.VISIBLE));
    baseline = state == null ? fingerprint() : state.getString("baseline", fingerprint());
    redraw();
  }

  private void jumpCampus(RouteStore.Route campus) {
    if (pane.map == null) return;
    java.util.List<ReplayEngine.Point> p = new java.util.ArrayList<>();
    for (ReplayEngine.Point q : campus.points)
      p.add(MapCoordinates.convert(q, campus.coordinateSystem, route.coordinateSystem));
    pane.fit(p);
  }

  private void askName() {
    if (!editable()) return;
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setText(name.getText());
    new AlertDialog.Builder(this)
        .setTitle("路线名称")
        .setView(input)
        .setPositiveButton(
            "预览保存",
            (d, w) -> {
              name.setText(input.getText());
              save(true);
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void coordinates() {
    if (!editable() || selected < 0 || selected >= draft.vertices().size()) return;
    ReplayEngine.Point p = draft.vertices().get(selected);
    EditText input = new EditText(this);
    input.setText(p.lat + "," + p.lon);
    new AlertDialog.Builder(this)
        .setTitle(route.coordinateSystem + " 纬度,经度")
        .setView(input)
        .setPositiveButton(
            "应用",
            (d, w) -> {
              try {
                String[] pair = input.getText().toString().split(",");
                draft.move(
                    selected,
                    new ReplayEngine.Point(
                        Double.parseDouble(pair[0]), Double.parseDouble(pair[1])));
                redraw();
              } catch (RuntimeException e) {
                Toast.makeText(this, "坐标无效", Toast.LENGTH_LONG).show();
              }
            })
        .setNegativeButton("取消", null)
        .show();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (pane != null) pane.start();
  }

  @Override
  protected void onStop() {
    if (pane != null) pane.stop();
    super.onStop();
  }

  private boolean editable() {
    com.zcshou.service.ServiceGo service = com.zcshou.service.ServiceGo.current();
    if (service != null && service.replayLocked()) {
      Toast.makeText(this, "请先停止复演后编辑", Toast.LENGTH_LONG).show();
      return false;
    }
    return true;
  }

  private void mapReady() {
    pane.map.setOnMapClickListener(ll -> {
      selected = -1;
      pane.highlight(-1);
    });
    pane.map.setOnMarkerClickListener(marker -> {
      Object tag = marker.getTag();
      if (tag instanceof Integer) {
        selected = (Integer) tag;
        pane.highlight(selected);
        tools.setVisibility(android.view.View.VISIBLE);
        summary.setText("已选点 " + (selected + 1) + "，可拖动或删除");
      }
      return true;
    });
    pane.map.setOnMarkerDragListener(
        new TencentMap.OnMarkerDragListener() {
          public void onMarkerDragStart(Marker m) {}

          public void onMarkerDrag(Marker m) {}

          public void onMarkerDragEnd(Marker m) {
            if (editable()) {
              Object tag = m.getTag();
              if (tag instanceof Integer) draft.move((Integer) tag, from(m.getPosition()));
            }
            redraw();
          }
        });
    pane.map.setOnMapLongClickListener(ll -> {
      if (!editable()) return;
      List<ReplayEngine.Point> points = draft.route();
      if (points.size() < 2) return;
      android.graphics.Point tap = pane.map.getProjection().toScreenLocation(ll);
      double best = Double.MAX_VALUE;
      int segment = -1;
      double bestX = 0, bestY = 0;
      for (int i = 0; i < points.size() - 1; i++) {
        android.graphics.Point
            a = pane.map.getProjection().toScreenLocation(pane.coordinateNative(points.get(i))),
            b = pane.map.getProjection().toScreenLocation(pane.coordinateNative(points.get(i + 1)));
        double dx = b.x - a.x, dy = b.y - a.y;
        double t =
            dx * dx + dy * dy == 0
                ? 0
                : Math.max(
                    0,
                    Math.min(1, ((tap.x - a.x) * dx + (tap.y - a.y) * dy) / (dx * dx + dy * dy)));
        double x = a.x + t * dx, y = a.y + t * dy, d = Math.hypot(tap.x - x, tap.y - y);
        if (d < best) {
          best = d;
          segment = i;
          bestX = x;
          bestY = y;
        }
      }
      if (best > 32 * getResources().getDisplayMetrics().density) {
        Toast.makeText(this, "请长按要插点的线段附近", Toast.LENGTH_SHORT).show();
        return;
      }
      final int after = segment;
      final ReplayEngine.Point point =
          from(
              pane.map
                  .getProjection()
                  .fromScreenLocation(new android.graphics.Point((int) bestX, (int) bestY)));
      Marker preview =
          pane.map.addMarker(
              new MarkerOptions().position(pane.coordinateNative(point)).title("待插入点"));
      preview.showInfoWindow();
      new AlertDialog.Builder(this)
          .setMessage("在选中线段插入此点？")
          .setPositiveButton(
              "插入",
              (d, w) -> {
                if (editable()) {
                  draft.insert(after, point);
                  redraw();
                }
              })
          .setNegativeButton("取消", null)
          .setOnDismissListener(d -> preview.remove())
          .show();
      redraw();
    });
    redraw();
    if (draft.vertices().isEmpty()) {
      com.zcshou.service.ServiceGo current = com.zcshou.service.ServiceGo.current();
      jumpCampus(current == null ? RouteStore.nanhu() : current.selectedRoute);
    } else pane.fit(draft.route());
  }

  private ReplayEngine.Point from(LatLng p) {
    return MapCoordinates.convert(
        new ReplayEngine.Point(p.latitude, p.longitude),
        CoordinateSystem.GCJ02,
        route.coordinateSystem);
  }

  private void redraw() {
    closed.setChecked(draft.closed());
    pane.draw(draft.route(), editableQuiet());
    pane.highlight(selected);
    undoButton.setEnabled(editableQuiet() && draft.canUndo());
    redoButton.setEnabled(editableQuiet() && draft.canRedo());
    summary.setText(
        String.format(
            Locale.CHINA,
            "%d 点 · %.3f km\n移动地图后点“添加中心点”；长按线段插点；点选标记可编辑",
            draft.vertices().size(),
            draftMetres() / 1000));
  }

  private java.util.List<ReplayEngine.Point> draftWgs() {
    java.util.List<ReplayEngine.Point> result = new java.util.ArrayList<>();
    for (ReplayEngine.Point p : draft.route())
      result.add(MapCoordinates.convert(p, route.coordinateSystem, CoordinateSystem.WGS84));
    return result;
  }

  private double draftMetres() {
    java.util.List<ReplayEngine.Point> p = draftWgs();
    double sum = 0;
    for (int i = 1; i < p.size(); i++) sum += ReplayEngine.metres(p.get(i - 1), p.get(i));
    return sum;
  }

  private boolean editableQuiet() {
    com.zcshou.service.ServiceGo s = com.zcshou.service.ServiceGo.current();
    return s == null || !s.replayLocked();
  }

  private String fingerprint() {
    StringBuilder b = new StringBuilder(name.getText()).append(draft.closed());
    for (ReplayEngine.Point p : draft.vertices())
      b.append('|').append(p.lat).append(',').append(p.lon);
    return b.toString();
  }

  private void save(boolean exit) {
    if (!editable()) return;
    try {
      ReplayEngine test = new ReplayEngine();
      test.start(draftWgs(), 1, null, 16, 0);
      if (name.getText().toString().trim().isEmpty()) throw new IllegalArgumentException("请输入路线名称");
      new AlertDialog.Builder(this)
          .setTitle("保存路线")
          .setMessage(
              String.format(
                  Locale.CHINA,
                  "%s\n%d 点 · %.3f km",
                  name.getText(),
                  draft.vertices().size(),
                  draftMetres() / 1000))
          .setPositiveButton(
              "保存",
              (d, w) -> {
                route.name = name.getText().toString();
                route.points.clear();
                route.points.addAll(draft.route());
                store.save(route);
                setResult(
                    RESULT_OK, new android.content.Intent().putExtra("saved_route_id", route.id));
                baseline = fingerprint();
                Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                MobileUi.detail(this, route.id);
                finish();
              })
          .setNegativeButton("继续编辑", null)
          .show();
    } catch (RuntimeException e) {
      Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private void add(LinearLayout root, String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    root.addView(b);
    b.setOnClickListener(v -> {
      if (editable()) {
        action.run();
        redraw();
      }
    });
  }

  @Override
  public void onBackPressed() {
    if (fingerprint().equals(baseline)) {
      super.onBackPressed();
      return;
    }
    new AlertDialog.Builder(this)
        .setMessage("路线尚未保存")
        .setPositiveButton("保存", (d, w) -> save(true))
        .setNegativeButton("放弃", (d, w) -> finish())
        .setNeutralButton("继续编辑", null)
        .show();
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    return draft;
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    double[] p = new double[draft.vertices().size() * 2];
    for (int i = 0; i < draft.vertices().size(); i++) {
      p[i * 2] = draft.vertices().get(i).lat;
      p[i * 2 + 1] = draft.vertices().get(i).lon;
    }
    out.putDoubleArray("points", p);
    out.putBoolean("closed", draft.closed());
    out.putString("coordinateSystem", route.coordinateSystem.name());
    out.putLong("id", route.id);
    out.putString("name", name.getText().toString());
    out.putString("baseline", baseline);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (pane != null) {
      pane.resume();
      redraw();
    }
  }

  @Override
  protected void onPause() {
    if (pane != null) pane.pause();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (pane != null) pane.destroy();
    store.close();
    super.onDestroy();
  }
}
