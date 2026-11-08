package com.zcshou.replay;

import static org.junit.Assert.*;

import org.junit.Test;

public class CompletionGateTest {
  @Test
  public void idleAndRestoreNeverNotify() {
    CompletionGate g = new CompletionGate();
    assertEquals(-1, g.observe(ReplayEngine.State.COMPLETED));
    assertEquals(-1, g.observe(ReplayEngine.State.IDLE));
  }

  @Test
  public void oneCompletionPerStart() {
    CompletionGate g = new CompletionGate();
    g.begin();
    long token = g.observe(ReplayEngine.State.COMPLETED);
    assertTrue(token >= 0);
    assertTrue(g.valid(token));
    assertEquals(-1, g.observe(ReplayEngine.State.COMPLETED));
  }

  @Test
  public void stopAndPauseAreSilent() {
    CompletionGate g = new CompletionGate();
    g.begin();
    assertEquals(-1, g.observe(ReplayEngine.State.PAUSED));
    assertEquals(-1, g.observe(ReplayEngine.State.STOPPED));
    assertEquals(-1, g.observe(ReplayEngine.State.COMPLETED));
  }

  @Test
  public void nextRunInvalidatesQueuedNotice() {
    CompletionGate g = new CompletionGate();
    g.begin();
    long old = g.observe(ReplayEngine.State.COMPLETED);
    g.begin();
    assertFalse(g.valid(old));
    assertTrue(g.observe(ReplayEngine.State.COMPLETED) >= 0);
  }

  @Test
  public void destroyInvalidatesQueuedNotice() {
    CompletionGate g = new CompletionGate();
    g.begin();
    long old = g.observe(ReplayEngine.State.COMPLETED);
    g.invalidate();
    assertFalse(g.valid(old));
  }

  @Test
  public void prepareDoesNotRearmOldCompletedRun() {
    CompletionGate g = new CompletionGate();
    g.begin();
    g.observe(ReplayEngine.State.COMPLETED);
    g.observe(ReplayEngine.State.IDLE);
    assertEquals(-1, g.observe(ReplayEngine.State.COMPLETED));
  }
}
