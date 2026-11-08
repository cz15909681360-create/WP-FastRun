package com.zcshou.replay;

public final class RouteActivity extends android.app.Activity {
  @Override
  public void onCreate(android.os.Bundle state) {
    super.onCreate(state);
    startActivity(
        new android.content.Intent(this, CampusActivity.class).putExtra("open_routes", true));
    finish();
  }
}
