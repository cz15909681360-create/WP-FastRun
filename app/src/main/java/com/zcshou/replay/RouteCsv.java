package com.zcshou.replay;

import java.util.ArrayList;
import java.util.List;

/** Explicit latitude,longitude CSV. No network or map SDK calls. */
public final class RouteCsv {
  private RouteCsv() {}

  public static final class Document {
    public final CoordinateSystem coordinateSystem; public final List<ReplayEngine.Point> points;
    Document(CoordinateSystem s,List<ReplayEngine.Point> p){coordinateSystem=s;points=p;}
  }
  public static Document parseDocument(String text) {
    CoordinateSystem system=CoordinateSystem.WGS84;
    String[] rows=text.replace("\ufeff", "").split("\\r\\n|\\r|\\n",-1);
    StringBuilder plain=new StringBuilder();boolean marked=false;
    for(int i=0;i<rows.length;i++){
      String row=rows[i], trimmed=row.trim();
      if(trimmed.replace("#", "").trim().toLowerCase(java.util.Locale.ROOT).startsWith("coordinatesystem")){
        if(trimmed.startsWith("#")||i!=0||marked)throw new IllegalArgumentException("CSV 第 "+(i+1)+" 行：坐标系声明必须唯一且位于首行，不能使用注释");
        String[] pair=trimmed.split(",",-1);
        try{if(pair.length!=2||!pair[0].equals("coordinateSystem"))throw new IllegalArgumentException();system=CoordinateSystem.valueOf(pair[1]);}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("CSV 第 "+(i+1)+" 行：坐标系须为 WGS84、GCJ02 或 BD09");}
        marked=true;plain.append('\n');
      }else plain.append(row).append('\n');
    }
    return new Document(system,parse(plain.toString()));
  }
  public static List<ReplayEngine.Point> parse(String text) {
    List<ReplayEngine.Point> points = new ArrayList<>();
    String[] lines = text.replace("\ufeff", "").split("\\r\\n|\\r|\\n");
    boolean headerSeen=false;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if(line.equalsIgnoreCase("latitude,longitude")){if(headerSeen||!points.isEmpty())throw new IllegalArgumentException("CSV 第 "+(i+1)+" 行：列名不能重复或出现在坐标之后");headerSeen=true;continue;}
      if (line.isEmpty() || line.startsWith("#"))
        continue;
      String[] pair = line.split(",", -1);
      try {
        if (pair.length != 2) throw new IllegalArgumentException();
        points.add(
            new ReplayEngine.Point(
                Double.parseDouble(pair[0].trim()), Double.parseDouble(pair[1].trim())));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("CSV 第 " + (i + 1) + " 行无效，格式须为 latitude,longitude");
      }
      if (points.size() > 10000) throw new IllegalArgumentException("路线最多 10000 点");
    }
    ReplayEngine check = new ReplayEngine();
    check.start(points, 1, null, 8, 0);
    return points;
  }
}
