package com.zcshou.replay;

import com.zcshou.utils.MapUtils;

/** Local coordinate adapter. Reverse conversion is numerical, not a Tencent API. */
public final class MapCoordinates {
  private MapCoordinates() {}

  public static ReplayEngine.Point convert(ReplayEngine.Point p, CoordinateSystem from, CoordinateSystem to) {
    if(from==to)return p;
    ReplayEngine.Point w=p;
    if(from==CoordinateSystem.GCJ02)w=fromMap(p);
    if(from==CoordinateSystem.BD09){double[] v=MapUtils.bd09togcj02(p.lon,p.lat);w=fromMap(new ReplayEngine.Point(v[1],v[0]));}
    if(to==CoordinateSystem.WGS84)return w;
    if(to==CoordinateSystem.GCJ02)return toMap(w);
    double[] b=MapUtils.wgs2bd09(w.lon,w.lat);return new ReplayEngine.Point(b[1],b[0]);
  }
  private static boolean outside(ReplayEngine.Point p) {
    return p.lon < 72.004 || p.lon > 137.8347 || p.lat < 0.8293 || p.lat > 55.8271;
  }

  public static ReplayEngine.Point toMap(ReplayEngine.Point p) {
    if (outside(p)) return p;
    double[] inverse = MapUtils.gcj02towgs84(p.lon, p.lat);
    return new ReplayEngine.Point(2 * p.lat - inverse[1], 2 * p.lon - inverse[0]);
  }

  public static ReplayEngine.Point fromMap(ReplayEngine.Point p) {
    if (outside(p)) return p;
    ReplayEngine.Point guess = p;
    for (int i = 0; i < 8; i++) {
      ReplayEngine.Point projected = toMap(guess);
      guess =
          new ReplayEngine.Point(
              guess.lat + p.lat - projected.lat, guess.lon + p.lon - projected.lon);
    }
    return guess;
  }
}
