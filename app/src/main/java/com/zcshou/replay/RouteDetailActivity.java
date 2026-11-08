package com.zcshou.replay;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.*;

public final class RouteDetailActivity extends Activity {
  private TencentMapPane pane;
  private RouteStore.Route route;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    MobileUi.window(this);
    route = MobileUi.find(this, getIntent().getLongExtra("id", -1));
    setTitle(route.name);
    LinearLayout root = MobileUi.column(this);
    setContentView(root);
    root.addView(MobileUi.text(this, route.name, 24));
    pane =
        new TencentMapPane(
            this,
            () -> {
              pane.draw(route.points, false);
              pane.fit(route.points);
            });
    pane.coordinateSystem = route.coordinateSystem;
    root.addView(pane, new LinearLayout.LayoutParams(-1, 0, 1));
    root.addView(
        MobileUi.text(
            this,
            String.format(
                Locale.CHINA,
                "%.3f km · %d 点 · %s",
                MobileUi.metres(route) / 1000,
                route.points.size(),
                ReplayEngine.metres(route.points.get(0), route.points.get(route.points.size() - 1))
                        < 0.01
                    ? "闭合路线"
                    : "开放路线"),
            16));
    MobileUi.button(
        root,
        "使用此路线",
        () -> {
          if (MobileUi.locked()) throw new IllegalStateException("请先停止当前任务");
          startActivity(
              new Intent(this, CampusActivity.class)
                  .putExtra("selected_route_id", route.id)
                  .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
          finish();
        });
    MobileUi.button(
        root, route.builtinKey != null ? "复制并编辑" : "编辑路线", () -> MobileUi.edit(this, route));
    if (route.builtinKey == null)
      MobileUi.button(
          root,
          "更多",
          () ->
              new AlertDialog.Builder(this)
                  .setItems(
                      new String[] {"重命名", "删除路线"},
                      (d, n) -> {
                        try {
                          MobileUi.editable();
                          if (n == 1)
                            new AlertDialog.Builder(this)
                                .setMessage("删除此路线？")
                                .setPositiveButton(
                                    "删除",
                                    (x, y) -> {
                                      try (RouteStore s = new RouteStore(this)) {
                                        s.delete(route.id);
                                      }
                                      finish();
                                    })
                                .setNegativeButton("取消", null)
                                .show();
                          else {
                            EditText name = new EditText(this);
                            name.setText(route.name);
                            new AlertDialog.Builder(this)
                                .setTitle("重命名")
                                .setView(name)
                                .setPositiveButton(
                                    "保存",
                                    (x, y) -> {
                                      if (!name.getText().toString().trim().isEmpty()) {
                                        route.name = name.getText().toString();
                                        try (RouteStore s = new RouteStore(this)) {
                                          s.save(route);
                                        }
                                        recreate();
                                      }
                                    })
                                .setNegativeButton("取消", null)
                                .show();
                          }
                        } catch (RuntimeException e) {
                          Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                      })
                  .show());
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (pane != null) pane.start();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (pane != null) pane.resume();
  }

  @Override
  protected void onPause() {
    if (pane != null) pane.pause();
    super.onPause();
  }

  @Override
  protected void onStop() {
    if (pane != null) pane.stop();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (pane != null) pane.destroy();
    super.onDestroy();
  }
}
