package com.zcshou.replay;

import java.util.ArrayList;
import java.util.List;

/** Pure Java route integrator. Coordinates are WGS84, distances are metres. */
public final class ReplayEngine {
  public enum State {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED
  }

  public static final class Point {
    public final double lat, lon;

    public Point(double lat, double lon) {
      if (!Double.isFinite(lat)
          || !Double.isFinite(lon)
          || Math.abs(lat) > 90
          || Math.abs(lon) > 180) throw new IllegalArgumentException("无效坐标");
      this.lat = lat;
      this.lon = lon;
    }
  }

  public static final class Snapshot {
    public final State state;
    public final Point point;
    public final double metres, target, speedKmh, bearing, lapLength;
    public final long activeMillis;

    Snapshot(State s, Point p, double d, double t, double v, double b, double l, long time) {
      state = s;
      point = p;
      metres = d;
      target = t;
      speedKmh = v;
      bearing = b;
      lapLength = l;
      activeMillis = time;
    }
  }

  private final List<Point> points = new ArrayList<>();
  private double[] cumulative;
  private State state = State.IDLE;
  private double distance, target, speed = 16, length;
  private long last, active;

  public synchronized void prepare(List<Point> source, double kmh) {
    if (locked()) throw new IllegalStateException("任务尚未停止");
    start(source, 1, null, kmh, 0);
    state = State.IDLE;
    distance = 0;
    active = 0;
  }

  public synchronized boolean locked() {
    return state == State.RUNNING || state == State.PAUSED;
  }

  public synchronized void start(
      List<Point> source, int laps, Double targetMetres, double kmh, long now) {
    if (locked()) throw new IllegalStateException("请先停止当前复演");
    if (laps < 1 || (targetMetres != null && (!Double.isFinite(targetMetres) || targetMetres <= 0)))
      throw new IllegalArgumentException("请输入有效的圈数或距离");
    validateSpeed(kmh);
    List<Point> clean = new ArrayList<>();
    for (Point p : source)
      if (clean.isEmpty() || metres(clean.get(clean.size() - 1), p) > 0.01) clean.add(p);
    if (clean.size() < 2) throw new IllegalArgumentException("路线至少需要两个不同点");
    if (laps > 1 || targetMetres != null) {
      if (metres(clean.get(clean.size() - 1), clean.get(0)) > 0.01) clean.add(clean.get(0));
    }
    double[] sums = new double[clean.size()];
    for (int i = 1; i < clean.size(); i++)
      sums[i] = sums[i - 1] + metres(clean.get(i - 1), clean.get(i));
    double total = sums[sums.length - 1];
    if (total < 0.1) throw new IllegalArgumentException("路线过短");
    points.clear();
    points.addAll(clean);
    cumulative = sums;
    length = total;
    target = targetMetres == null ? length * laps : targetMetres;
    distance = 0;
    active = 0;
    speed = kmh;
    last = now;
    state = State.RUNNING;
  }

  public synchronized void tick(long now) {
    if (state != State.RUNNING) return;
    long dt = Math.max(0, now - last);
    last = now;
    double remaining = target - distance;
    double step = speed / 3.6 * dt / 1000.0;
    active += (long) Math.min(dt, remaining / (speed / 3.6) * 1000.0);
    distance = Math.min(target, distance + step);
    if (distance >= target) state = State.COMPLETED;
  }

  public synchronized void pause(long now) {
    tick(now);
    if (state == State.RUNNING) state = State.PAUSED;
  }

  public synchronized void resume(long now) {
    if (state == State.PAUSED) {
      last = now;
      state = State.RUNNING;
    }
  }

  public synchronized void stop(long now) {
    tick(now);
    state = State.STOPPED;
  }

  public synchronized void speed(double kmh, long now) {
    validateSpeed(kmh);
    tick(now);
    speed = kmh;
  }

  private static void validateSpeed(double v) {
    if (!Double.isFinite(v) || v <= 0 || v > 100)
      throw new IllegalArgumentException("速度须大于 0 且不超过 100 km/h");
  }

  public synchronized Snapshot snapshot() {
    if (points.isEmpty()) return new Snapshot(state, null, 0, 0, speed, 0, 0, active);
    double along = distance % length;
    if (distance > 0 && Math.abs(along) < 0.000001) along = length;
    int i = 1;
    while (i < cumulative.length - 1 && cumulative[i] < along) i++;
    Point a = points.get(i - 1), b = points.get(i);
    double fraction = (along - cumulative[i - 1]) / (cumulative[i] - cumulative[i - 1]);
    Point p = new Point(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction);
    double x = Math.sin(Math.toRadians(b.lon - a.lon)) * Math.cos(Math.toRadians(b.lat));
    double y =
        Math.cos(Math.toRadians(a.lat)) * Math.sin(Math.toRadians(b.lat))
            - Math.sin(Math.toRadians(a.lat))
                * Math.cos(Math.toRadians(b.lat))
                * Math.cos(Math.toRadians(b.lon - a.lon));
    return new Snapshot(
        state,
        p,
        distance,
        target,
        speed,
        (Math.toDegrees(Math.atan2(x, y)) + 360) % 360,
        length,
        active);
  }

  public static double metres(Point a, Point b) {
    double x = Math.sin(Math.toRadians(b.lat - a.lat) / 2),
        y = Math.sin(Math.toRadians(b.lon - a.lon) / 2);
    double h = x * x + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * y * y;
    return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
  }
}
