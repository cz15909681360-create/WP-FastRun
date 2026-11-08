package com.zcshou.replay;

import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A single non-interactive five-second completion card. */
public final class CompletionNotice {
  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private View card;
  private ViewGroup inApp;

  public CompletionNotice(Context c) {
    context = c;
  }

  public void show() {
    clear();
    TextView label = new TextView(context);
    label.setText("路线已完成，请记得结束校园跑");
    label.setTextSize(16);
    label.setTextColor(0xff143d35);
    int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
    label.setPadding(pad, pad, pad, pad);
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable();
    bg.setColor(0xf0e0f3ed);
    bg.setCornerRadius(pad);
    label.setBackground(bg);
    try {
      if (android.provider.Settings.canDrawOverlays(context)) {
        WindowManager.LayoutParams p =
            new WindowManager.LayoutParams(
                (int) (290 * context.getResources().getDisplayMetrics().density),
                -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = pad * 5;
        ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).addView(label, p);
        card = label;
      } else {
        android.app.Activity a = com.zcshou.gogogo.GoApplication.currentActivity.get();
        if (a != null && !a.isFinishing() && a.hasWindowFocus()) {
          inApp = a.findViewById(android.R.id.content);
          FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
          p.setMargins(pad, pad, pad, pad);
          inApp.addView(label, p);
          card = label;
        } else Toast.makeText(context, "路线已完成，请记得结束校园跑", Toast.LENGTH_LONG).show();
      }
    } catch (RuntimeException e) {
      Toast.makeText(context, "路线已完成，请记得结束校园跑", Toast.LENGTH_LONG).show();
    }
    handler.postDelayed(this::clear, 5000);
  }

  public void clear() {
    handler.removeCallbacksAndMessages(null);
    if (card != null) {
      try {
        if (inApp != null) inApp.removeView(card);
        else ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).removeView(card);
      } catch (IllegalArgumentException ignored) {
      }
      card = null;
      inApp = null;
    }
  }
}
