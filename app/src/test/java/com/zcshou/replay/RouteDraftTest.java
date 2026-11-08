package com.zcshou.replay;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class RouteDraftTest {
  private final ReplayEngine.Point a = new ReplayEngine.Point(30.513377, 114.330942),
      b = new ReplayEngine.Point(30.507876, 114.329641),
      c = new ReplayEngine.Point(30.508882, 114.324538);

  @Test
  public void closedEndpointFollowsDraggedStart() {
    RouteDraft d = new RouteDraft(Arrays.asList(a, b, a));
    assertEquals(2, d.vertices().size());
    d.move(0, c);
    assertSame(c, d.route().get(2));
    d.undo();
    assertSame(a, d.route().get(2));
    d.redo();
    assertSame(c, d.route().get(0));
  }

  @Test
  public void insertDeleteAndClosureUndoAsSingleChanges() {
    RouteDraft d = new RouteDraft(Arrays.asList(a, b));
    d.insert(0, c);
    d.delete(1);
    d.undo();
    assertSame(c, d.vertices().get(1));
    d.undo();
    assertEquals(2, d.vertices().size());
    d.closed(true);
    assertEquals(3, d.route().size());
    d.undo();
    assertFalse(d.closed());
    d.redo();
    assertTrue(d.closed());
  }

  @Test
  public void unchangedPointsKeepOriginalPrecision() {
    RouteDraft d = new RouteDraft(Arrays.asList(a, b));
    assertSame(a, d.route().get(0));
    d.add(c);
    d.undo();
    assertSame(b, d.route().get(1));
  }

  @Test
  public void editingDoesNotMutateSource() {
    List<ReplayEngine.Point> source = new ArrayList<>(Arrays.asList(a, b));
    RouteDraft d = new RouteDraft(source);
    d.move(0, c);
    assertSame(a, source.get(0));
  }

  @Test
  public void mapCoordinateRoundTrip() {
    for (ReplayEngine.Point p : Arrays.asList(a, b, c)) {
      ReplayEngine.Point q = MapCoordinates.fromMap(MapCoordinates.toMap(p));
      assertTrue(ReplayEngine.metres(p, q) < 0.02);
    }
  }

  @Test
  public void outOfChinaCoordinatesUnchanged() {
    ReplayEngine.Point p = new ReplayEngine.Point(51.5, -0.1);
    assertSame(p, MapCoordinates.toMap(p));
    assertSame(p, MapCoordinates.fromMap(p));
  }
}
