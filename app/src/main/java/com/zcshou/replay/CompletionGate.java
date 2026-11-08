package com.zcshou.replay;

/** Consumes natural completion once per successful start; nothing is restored from disk. */
public final class CompletionGate {
  private long generation;
  private boolean armed, consumed;

  public synchronized void begin() {
    generation++;
    armed = true;
    consumed = false;
  }

  public synchronized long observe(ReplayEngine.State state) {
    if (state == ReplayEngine.State.STOPPED) armed = false;
    if (state != ReplayEngine.State.COMPLETED || !armed || consumed) return -1;
    consumed = true;
    return generation;
  }

  public synchronized boolean valid(long token) {
    return token == generation && armed && consumed;
  }

  public synchronized void invalidate() {
    generation++;
    armed = false;
  }
}
