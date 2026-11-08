package com.zcshou.replay;

import java.util.*;

/** WGS84 draft. A drag commits once on release; closure is represented separately. */
public final class RouteDraft {
  private static final class Revision {
    final List<ReplayEngine.Point> points;
    final boolean closed;

    Revision(List<ReplayEngine.Point> p, boolean c) {
      points = new ArrayList<>(p);
      closed = c;
    }
  }

  private final Deque<Revision> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private final List<ReplayEngine.Point> points = new ArrayList<>();
  private boolean closed;

  public RouteDraft(List<ReplayEngine.Point> source) {
    points.addAll(source);
    if (points.size() > 1
        && ReplayEngine.metres(points.get(0), points.get(points.size() - 1)) < 0.01) {
      closed = true;
      points.remove(points.size() - 1);
    }
  }

  public static RouteDraft fresh(){RouteDraft d=new RouteDraft(java.util.Collections.emptyList());d.closed=true;return d;}
  private void checkpoint() {
    undo.push(new Revision(points, closed));
    redo.clear();
  }

  public List<ReplayEngine.Point> vertices() {
    return Collections.unmodifiableList(points);
  }

  public boolean closed() {
    return closed;
  }

  public void add(ReplayEngine.Point p) {
    checkpoint();
    points.add(p);
  }

  public void insert(int after, ReplayEngine.Point p) {
    checkpoint();
    points.add(after + 1, p);
  }

  public void move(int i, ReplayEngine.Point p) {
    checkpoint();
    points.set(i, p);
  }

  public void delete(int i) {
    checkpoint();
    points.remove(i);
  }

  public void closed(boolean c) {
    if (c != closed) {
      checkpoint();
      closed = c;
    }
  }

  public boolean canUndo(){return !undo.isEmpty();}
  public boolean canRedo(){return !redo.isEmpty();}
  public void undo() {
    if (undo.isEmpty()) return;
    redo.push(new Revision(points, closed));
    restore(undo.pop());
  }

  public void redo() {
    if (redo.isEmpty()) return;
    undo.push(new Revision(points, closed));
    restore(redo.pop());
  }

  private void restore(Revision r) {
    points.clear();
    points.addAll(r.points);
    closed = r.closed;
  }

  public List<ReplayEngine.Point> route() {
    List<ReplayEngine.Point> p = new ArrayList<>(points);
    if (closed && p.size() > 1) p.add(p.get(0));
    return p;
  }

  public double metres() {
    List<ReplayEngine.Point> p = route();
    double sum = 0;
    for (int i = 1; i < p.size(); i++) sum += ReplayEngine.metres(p.get(i - 1), p.get(i));
    return sum;
  }
}
