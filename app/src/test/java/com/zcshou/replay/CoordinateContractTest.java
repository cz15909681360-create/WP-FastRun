package com.zcshou.replay;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoordinateContractTest {
 @Test public void csvTaggedAndLegacy(){
  RouteCsv.Document d=RouteCsv.parseDocument("coordinateSystem,GCJ02\nlatitude,longitude\n30.511,114.328\n30.512,114.329");
  assertEquals(CoordinateSystem.GCJ02,d.coordinateSystem);assertEquals(114.328,d.points.get(0).lon,0);
  assertEquals(CoordinateSystem.WGS84,RouteCsv.parseDocument("30,114\n31,115").coordinateSystem);
 }
 @Test public void legacyRejectsGcj(){try{RouteCsv.parse("coordinateSystem,GCJ02\n30,114\n31,115");fail();}catch(IllegalArgumentException expected){}}
 @Test public void invalidMetadata(){for(String s:new String[]{"coordinateSystem,OTHER\n30,114\n31,115","coordinateSystem,GCJ02\ncoordinateSystem,WGS84\n30,114\n31,115"}){try{RouteCsv.parseDocument(s);fail();}catch(IllegalArgumentException expected){}}}
 @Test public void fixedCoordinates(){
 check(30.511,114.328,30.508603768601958,114.33347258003712);
 check(39.908,116.397,39.90940335390724,116.40324337854781);
 check(31.2304,121.4737,31.22845773757727,121.47822305927693);
 check(51.5074,-0.1278,51.5074,-0.1278);
 }
 private void check(double lat,double lon,double glat,double glon){
  ReplayEngine.Point w=new ReplayEngine.Point(lat,lon),g=MapCoordinates.toMap(w);
  assertEquals(glat,g.lat,1e-10);assertEquals(glon,g.lon,1e-10);
  ReplayEngine.Point n=MapCoordinates.fromMap(g);assertEquals(lat,n.lat,1e-8);assertEquals(lon,n.lon,1e-8);
  assertSame(g,MapCoordinates.convert(g,CoordinateSystem.GCJ02,CoordinateSystem.GCJ02));
 }
}