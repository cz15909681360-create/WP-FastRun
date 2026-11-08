package com.zcshou.replay;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class ReplayEngineTest {
  private final ReplayEngine.Point a = new ReplayEngine.Point(30, 120);
  private final ReplayEngine.Point b = new ReplayEngine.Point(30, 120.001);
  private final ReplayEngine.Point c = new ReplayEngine.Point(30.001, 120.001);

  @Test
  public void straightDistanceAndSpeedChange() {
    ReplayEngine e = new ReplayEngine();
    e.start(Arrays.asList(a, b), 1, null, 3.6, 0);
    e.tick(10000);
    assertEquals(10, e.snapshot().metres, 0.00001);
    e.speed(7.2, 15000);
    e.tick(20000);
    assertEquals(25, e.snapshot().metres, 0.00001);
  }

  @Test
  public void pausedTimeExcludedAndResumeContinuous() {
    ReplayEngine e = new ReplayEngine();
    e.start(Arrays.asList(a, b), 1, null, 3.6, 0);
    e.pause(10000);
    e.tick(90000);
    assertEquals(10, e.snapshot().metres, 0.00001);
    e.resume(100000);
    e.tick(105000);
    assertEquals(15, e.snapshot().metres, 0.00001);
    assertEquals(15000, e.snapshot().activeMillis);
  }

  @Test
  public void crossesCornerAndClampsAtDistanceTarget() {
    ReplayEngine e = new ReplayEngine();
    double first = ReplayEngine.metres(a, b);
    e.start(Arrays.asList(a, b, c), 1, first + 10, 36, 0);
    e.tick(100000);
    assertEquals(ReplayEngine.State.COMPLETED, e.snapshot().state);
    assertEquals(first + 10, e.snapshot().metres, 0.00001);
    assertEquals(b.lon, e.snapshot().point.lon, 0.000001);
    assertTrue(e.snapshot().point.lat > b.lat);
  }

  @Test
  public void closedTwoLapsEndAtStart() {
    ReplayEngine e = new ReplayEngine();
    e.start(Arrays.asList(a, b, c), 2, null, 36, 0);
    e.tick(1000000);
    assertEquals(ReplayEngine.State.COMPLETED, e.snapshot().state);
    assertEquals(0, ReplayEngine.metres(a, e.snapshot().point), 0.001);
    assertEquals(e.snapshot().lapLength * 2, e.snapshot().metres, 0.001);
  }

  @Test
  public void stopAndRestartResetProgress() {
    ReplayEngine e = new ReplayEngine();
    e.start(Arrays.asList(a, b), 1, null, 3.6, 0);
    e.stop(5000);
    assertFalse(e.locked());
    assertEquals(5, e.snapshot().metres, 0.001);
    e.start(Arrays.asList(a, b), 1, null, 8, 10000);
    assertEquals(0, e.snapshot().metres, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsDegenerateRoute() {
    new ReplayEngine().start(Arrays.asList(a, a), 1, null, 8, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonfiniteSpeed() {
    new ReplayEngine().start(Arrays.asList(a, b), 1, null, Double.NaN, 0);
  }

  @Test
  public void duplicateAdjacentPointsDoNotBreakInterpolation() {
    ReplayEngine e = new ReplayEngine();
    e.start(Arrays.asList(a, a, b), 1, null, 8, 0);
    e.tick(1000);
    assertTrue(Double.isFinite(e.snapshot().point.lon));
  }
  @Test public void preparedSessionStaysAtStartAndDoesNotCountTime(){
    ReplayEngine engine=new ReplayEngine();engine.prepare(java.util.Arrays.asList(a,b),16);
    engine.tick(900000);ReplayEngine.Snapshot s=engine.snapshot();
    assertEquals(ReplayEngine.State.IDLE,s.state);assertEquals(16,s.speedKmh,0);assertEquals(0,s.metres,0);assertEquals(0,s.activeMillis);assertEquals(a.lat,s.point.lat,0);assertFalse(engine.locked());
  }
  @Test public void cannotInitializeOverPausedTask(){
    ReplayEngine engine=new ReplayEngine();engine.start(java.util.Arrays.asList(a,b),1,null,16,0);engine.pause(1000);
    try{engine.prepare(java.util.Arrays.asList(b,a),16);fail("must protect paused progress");}catch(IllegalStateException expected){}
    assertEquals(ReplayEngine.State.PAUSED,engine.snapshot().state);assertTrue(engine.snapshot().metres>0);
  }
}
