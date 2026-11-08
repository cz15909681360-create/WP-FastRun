package com.zcshou.replay;
import org.junit.Test;import static org.junit.Assert.*;
public class MobileDraftWorkflowTest {
 @Test public void newClosedDraftHasNoPhantomUndo(){RouteDraft d=RouteDraft.fresh();assertTrue(d.closed());assertFalse(d.canUndo());d.add(new ReplayEngine.Point(30,114));assertTrue(d.canUndo());d.undo();assertTrue(d.vertices().isEmpty());assertTrue(d.closed());assertFalse(d.canUndo());assertTrue(d.canRedo());}
 @Test public void editAfterUndoClearsRedoAndDoesNotLoseNewPoint(){RouteDraft d=RouteDraft.fresh();d.add(new ReplayEngine.Point(30,114));d.add(new ReplayEngine.Point(31,115));d.undo();d.add(new ReplayEngine.Point(32,116));assertFalse(d.canRedo());assertEquals(32,d.route().get(1).lat,0);assertEquals(d.route().get(0).lat,d.route().get(2).lat,0);}
}
