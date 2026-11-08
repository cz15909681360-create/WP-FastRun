package com.zcshou.replay;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import com.zcshou.service.ServiceGo;
import com.zcshou.utils.MapUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Same controller in Activity and application-overlay window. */
public final class ReplayPanel extends LinearLayout {
  private final ServiceGo service;
  private final RouteStore store;
  private final Spinner routes, mode;
  private final EditText speed, target;
  private final TextView status, preview;
  private final Button start;
  private List<RouteStore.Route> saved;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Runnable refresh =
      new Runnable() {
        public void run() {
          update();
          handler.postDelayed(this, 250);
        }
      };

  public ReplayPanel(Context c, ServiceGo service) {
    super(c);
    this.service = service;
    store = new RouteStore(c);
    setOrientation(VERTICAL);
    setPadding(16, 8, 16, 8);
    setBackgroundColor(0xfff5f5f5);
    routes = new Spinner(c);
    addView(routes);
    reload();
    mode = new Spinner(c);
    mode.setAdapter(
        new ArrayAdapter<>(
            c, android.R.layout.simple_spinner_dropdown_item, new String[] {"圈数", "目标距离（km）"}));
    addView(mode);
    speed = field("速度 km/h", String.valueOf(service.configuredSpeed));
    target = field("圈数 / 目标 km", String.valueOf(service.configuredTarget));
    mode.setSelection(service.configuredMode);
    for (int i = 0; i < saved.size(); i++)
      if (saved.get(i).id == service.selectedRoute.id) routes.setSelection(i);
    preview = new TextView(c);
    addView(preview);
    mode.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            if (!service.replayLocked() && position != service.configuredMode) {
              service.configuredMode = position;
              service.configuredTarget = position == 0 ? 1 : 3;
            }
            target.setText(String.valueOf(service.configuredTarget));
            updatePreview();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    routes.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            if (!service.replayLocked()
                && selected() != null
                && selected().id != service.selectedRoute.id) service.prepareSession(selected());
            updatePreview();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    status = new TextView(c);
    addView(status);
    start =
        button(
            "开始",
            () -> {
              RouteStore.Route route = selected();
              if (route == null) throw new IllegalArgumentException("请先在主界面保存路线");
              double goal = Double.parseDouble(target.getText().toString());
              boolean byDistance = mode.getSelectedItemPosition() == 1;
              if (!byDistance && (goal < 1 || goal != Math.rint(goal) || goal > Integer.MAX_VALUE))
                throw new IllegalArgumentException("圈数必须为正整数");
              service.startReplay(
                  route,
                  byDistance ? 1 : (int) goal,
                  byDistance ? goal * 1000 : null,
                  Double.parseDouble(speed.getText().toString()));
            });
    target.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int n, int f) {}

          public void onTextChanged(CharSequence s, int a, int before, int count) {}

          public void afterTextChanged(android.text.Editable e) {
            if (service.replayLocked()) return;
            try {
              double value = Double.parseDouble(e.toString());
              if (Double.isFinite(value) && value > 0) service.configuredTarget = value;
            } catch (NumberFormatException ignored) {
            }
          }
        });
    button("暂停 / 继续", () -> service.pauseOrResumeReplay());
    button("应用速度", () -> service.changeReplaySpeed(Double.parseDouble(speed.getText().toString())));
    button("停止复演", () -> service.stopReplay());
    button("刷新路线", this::reload);
    button(
        "结束定位服务",
        () -> getContext().stopService(new android.content.Intent(getContext(), ServiceGo.class)));
  }

  private EditText field(String label, String value) {
    TextView title = new TextView(getContext());
    title.setText(label);
    addView(title);
    EditText f = new EditText(getContext());
    f.setSingleLine(true);
    f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    f.setText(value);
    addView(f);
    return f;
  }

  private Button button(String label, Runnable action) {
    Button b = new Button(getContext());
    b.setText(label);MobileUi.style(b,label);
    addView(b);
    b.setOnClickListener(v -> {
      try {
        action.run();
        update();
      } catch (RuntimeException e) {
        Toast.makeText(
                getContext(), e.getMessage() == null ? "参数无效" : e.getMessage(), Toast.LENGTH_LONG)
            .show();
      }
    });
    return b;
  }

  private void reload() {
    saved = store.list();
    routes.setAdapter(
        new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, saved));
  }

  private RouteStore.Route selected() {
    int i = routes.getSelectedItemPosition();
    return i >= 0 && i < saved.size() ? saved.get(i) : null;
  }

  private void updatePreview() {
    if (preview == null) return;
    RouteStore.Route route = selected();
    if (route == null) {
      preview.setText("暂无路线");
      return;
    }
    List<ReplayEngine.Point> wgs = toWgs(route);
    double length = 0;
    for (int i = 1; i < wgs.size(); i++) length += ReplayEngine.metres(wgs.get(i - 1), wgs.get(i));
    boolean closed = mode.getSelectedItemPosition() == 1;
    try {
      closed |= Double.parseDouble(target.getText().toString()) > 1;
    } catch (NumberFormatException ignored) {
    }
    double closure = wgs.size() > 1 ? ReplayEngine.metres(wgs.get(wgs.size() - 1), wgs.get(0)) : 0;
    preview.setText(
        String.format(
            Locale.CHINA,
            "路线 %.3f km%s",
            (length + (closed ? closure : 0)) / 1000,
            closed ? String.format(Locale.CHINA, "（含返回起点 %.0f m）", closure) : ""));
  }

  public static List<ReplayEngine.Point> toWgs(RouteStore.Route route) {
    List<ReplayEngine.Point> result = new ArrayList<>();
    for (ReplayEngine.Point p : route.points) {
      result.add(MapCoordinates.convert(p,route.coordinateSystem,CoordinateSystem.WGS84));
    }
    return result;
  }

  private void update() {
    ReplayEngine.Snapshot s = service.replaySnapshot();
    boolean locked = service.replayLocked();
    routes.setEnabled(!locked);
    mode.setEnabled(!locked);
    target.setEnabled(!locked);
    start.setEnabled(!locked);
    status.setText(
        String.format(
            Locale.CHINA,
            "%s · %s\n%.3f / %.3f km · %.1f km/h\n完成圈数 %d · 用时 %d 秒",
            service.replayRouteName(),
            s.state,
            s.metres / 1000,
            s.target / 1000,
            s.speedKmh,
            s.lapLength > 0 ? (int) Math.floor(s.metres / s.lapLength) : 0,
            s.activeMillis / 1000));
    updatePreview();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    handler.post(refresh);
  }

  @Override
  protected void onDetachedFromWindow() {
    handler.removeCallbacks(refresh);
    store.close();
    super.onDetachedFromWindow();
  }
}
