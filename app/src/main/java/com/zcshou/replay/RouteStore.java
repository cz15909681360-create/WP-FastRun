package com.zcshou.replay;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;

/** Independent database; never upgrades or modifies the location-history database. */
public final class RouteStore extends SQLiteOpenHelper {
  public static final class Route {
    public long id;
    public CoordinateSystem coordinateSystem = CoordinateSystem.BD09;
    public String builtinKey;
    public String name;
    public final List<ReplayEngine.Point> points = new ArrayList<>(); // BD09

    public Route(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static Route nanhu() {
    Route r=new Route("南湖校区");r.id=-1;r.builtinKey="nanhu";r.coordinateSystem=CoordinateSystem.GCJ02;
    double[][] points={
      {30.509520763301413, 114.32901359993491},
      {30.508293531015692, 114.33578596938025},
      {30.505472991046634, 114.33509873647654},
      {30.505774426813765, 114.33338690134588},
      {30.505408397722455, 114.33306202756785},
      {30.505236148155277, 114.33271216341495},
      {30.50521461667092, 114.33238728963693},
      {30.505333038543544, 114.33197494976116},
      {30.505731364407268, 114.33162508556052},
      {30.506065095915783, 114.33160009532867},
      {30.506689493596767, 114.32811394891542},
      {30.5073677139869, 114.32801398798767},
      {30.50808899081261, 114.3282389006472},
      {30.508917914305457, 114.32876369647136},
      {30.509520763301413, 114.32901359993491}
    };
    for(double[] p:points)r.points.add(new ReplayEngine.Point(p[0],p[1]));return r;
  }
  public static Route yujia() {
    Route r=new Route("余家头校区");r.id=-2;r.builtinKey="yujia";r.coordinateSystem=CoordinateSystem.GCJ02;
    double[][] points={
      {30.607864840188483, 114.35439369658923},
      {30.607949765194444, 114.35530148661951},
      {30.608057336681156, 114.35604482192366},
      {30.60834041877896, 114.3578932929197},
      {30.605803973188028, 114.35840639143885},
      {30.605481250980215, 114.35661712424167},
      {30.60571338462277, 114.35652502953269},
      {30.60548691281196, 114.35489363896511},
      {30.605962502986852, 114.35566986514175},
      {30.60653434046297, 114.35602508735747},
      {30.606721177754345, 114.3559593053867},
      {30.607247717013568, 114.35582774167096},
      {30.60750249325869, 114.35539358125334},
      {30.60750815497227, 114.35538700304983},
      {30.60737793625328, 114.35505809366634},
      {30.607174115031402, 114.3549857335737},
      {30.60709485116945, 114.3549857335737},
      {30.60677213314391, 114.35502520275622},
      {30.606472061314552, 114.35509098460159},
      {30.606177650152528, 114.35518307921006},
      {30.606064414866857, 114.35523570473663},
      {30.606007797163606, 114.35538700304983},
      {30.605996473625453, 114.35549225407146},
      {30.606019120716617, 114.35580142893275},
      {30.60621162067785, 114.35597904000304},
      {30.60714580636243, 114.35580142893275},
      {30.60738925943549, 114.35570933427402},
      {30.607360951002235, 114.35507782820741},
      {30.606715515994736, 114.35505151539394},
      {30.606409782126107, 114.35513045370874},
      {30.60613801782192, 114.35564355240354},
      {30.606245591256048, 114.35582116344881},
      {30.60694198491127, 114.35586721082836},
      {30.607513816685547, 114.35524228293366}
    };
    for(double[] p:points)r.points.add(new ReplayEngine.Point(p[0],p[1]));return r;
  }

  public RouteStore(Context context) {
    super(context.getApplicationContext(), "replay_routes.db", null, 2);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE routes (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, points TEXT NOT NULL,"
            + " coordinate_system TEXT NOT NULL DEFAULT 'BD09')");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2)
      db.execSQL("ALTER TABLE routes ADD COLUMN coordinate_system TEXT NOT NULL DEFAULT 'BD09'");
  }

  public long save(Route route) {
    if (route.builtinKey != null) throw new IllegalArgumentException("内置路线请先复制");
    if (route.name.trim().isEmpty()) throw new IllegalArgumentException("请输入路线名称");
    JSONArray data = new JSONArray();
    try {
      for (ReplayEngine.Point p : route.points) data.put(new JSONArray().put(p.lat).put(p.lon));
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    ContentValues values = new ContentValues();
    values.put("name", route.name.trim());
    values.put("points", data.toString());
    values.put("coordinate_system", route.coordinateSystem.name());
    if (route.id == 0) route.id = getWritableDatabase().insertOrThrow("routes", null, values);
    else if (getWritableDatabase().update("routes", values, "_id=?", new String[] {"" + route.id})
        != 1) throw new IllegalStateException("路线已删除，请重新载入");
    return route.id;
  }

  public List<Route> list() {
    List<Route> routes = new ArrayList<>();
    routes.add(nanhu());
    routes.add(yujia());
    try (Cursor c =
        getReadableDatabase().query("routes", null, null, null, null, null, "_id DESC")) {
      while (c.moveToNext()) {
        Route r = new Route(c.getString(1));
        r.id = c.getLong(0);
        r.coordinateSystem = CoordinateSystem.valueOf(c.getString(3));
        try {
          JSONArray data = new JSONArray(c.getString(2));
          for (int i = 0; i < data.length(); i++) {
            JSONArray p = data.getJSONArray(i);
            r.points.add(new ReplayEngine.Point(p.getDouble(0), p.getDouble(1)));
          }
        } catch (JSONException e) {
          throw new IllegalStateException("路线数据损坏", e);
        }
        routes.add(r);
      }
    }
    return routes;
  }

  public void delete(long id) {
    if (id < 0) throw new IllegalArgumentException("不能删除内置路线");
    getWritableDatabase().delete("routes", "_id=?", new String[] {"" + id});
  }
}
