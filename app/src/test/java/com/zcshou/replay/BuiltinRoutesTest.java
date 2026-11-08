package com.zcshou.replay;
import org.junit.Test;
import static org.junit.Assert.*;
public class BuiltinRoutesTest {
 @Test public void nanhuReplacedWithNewGcjClosedRoute(){RouteStore.Route r=RouteStore.nanhu();assertEquals(CoordinateSystem.GCJ02,r.coordinateSystem);assertEquals(15,r.points.size());assertEquals(30.509520763301413,r.points.get(0).lat,0);assertEquals(114.32901359993491,r.points.get(0).lon,0);assertEquals(0,ReplayEngine.metres(r.points.get(0),r.points.get(14)),0);}
 @Test public void yujiaPreservesOpenRoute(){RouteStore.Route r=RouteStore.yujia();assertEquals(CoordinateSystem.GCJ02,r.coordinateSystem);assertEquals(34,r.points.size());assertTrue(ReplayEngine.metres(r.points.get(0),r.points.get(33))>1);assertEquals("yujia",r.builtinKey);}
}
