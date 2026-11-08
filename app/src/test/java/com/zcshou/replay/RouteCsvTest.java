package com.zcshou.replay;

import static org.junit.Assert.*;

import org.junit.Test;

public class RouteCsvTest {
  @Test
  public void acceptsUtf8BomCommentsAndWindowsLines() {
    assertEquals(
        2,
        RouteCsv.parse("\ufeff# WGS84\r\nlatitude,longitude\r\n30,120\r\n30.001,120.001\r\n")
            .size());
  }

  @Test
  public void preservesLatitudeLongitudeOrder() {
    ReplayEngine.Point p = RouteCsv.parse("30,120\n31,121").get(0);
    assertEquals(30, p.lat, 0);
    assertEquals(120, p.lon, 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsReversedOutOfRangeCoordinates() {
    RouteCsv.parse("120,30\n121,31");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsExtraColumn() {
    RouteCsv.parse("30,120,1\n31,121,2");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonfinite() {
    RouteCsv.parse("NaN,120\n31,121");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNoMovement() {
    RouteCsv.parse("30,120\n30,120");
  }

 @Test public void taggedErrorUsesPhysicalLine(){try{RouteCsv.parseDocument("coordinateSystem,GCJ02\nlatitude,longitude\nwrong,120");fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("3"));}}
 @Test(expected=IllegalArgumentException.class) public void rejectsCompactCommentDeclaration(){RouteCsv.parseDocument("#coordinateSystem,GCJ02\n30,120\n31,121");}
 @Test(expected=IllegalArgumentException.class) public void rejectsLateDeclaration(){RouteCsv.parseDocument("\ncoordinateSystem,GCJ02\n30,120\n31,121");}
 @Test public void supportsCrOnly(){assertEquals(2,RouteCsv.parseDocument("coordinateSystem,BD09\rlatitude,longitude\r30,120\r31,121").points.size());}
 @Test(expected=IllegalArgumentException.class) public void rejectsRepeatedHeader(){RouteCsv.parse("latitude,longitude\n30,120\nlatitude,longitude\n31,121");}
}
