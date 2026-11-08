package com.zcshou.gogogo;

import android.app.Application;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.ConsolePrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator;

import java.io.File;

public class GoApplication extends Application {
    public static java.lang.ref.WeakReference<android.app.Activity> currentActivity=new java.lang.ref.WeakReference<>(null);
    public static boolean campusFresh=true;
    private int visibleActivities;
    private final android.os.Handler visibilityHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable background=()->{campusFresh=true;com.zcshou.service.ServiceGo s=com.zcshou.service.ServiceGo.current();if(s!=null)s.appForeground(false);};
    private void trackVisibility(){registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
      public void onActivityStarted(android.app.Activity a){currentActivity=new java.lang.ref.WeakReference<>(a);visibleActivities++;visibilityHandler.removeCallbacks(background);com.zcshou.service.ServiceGo s=com.zcshou.service.ServiceGo.current();if(s!=null)s.appForeground(true);}
      public void onActivityStopped(android.app.Activity a){visibleActivities--;if(visibleActivities==0)visibilityHandler.postDelayed(background,650);}
      public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
      public void onActivityResumed(android.app.Activity a){}
      public void onActivityPaused(android.app.Activity a){}
      public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
      public void onActivityDestroyed(android.app.Activity a){}
    });}

    public static boolean mapsEnabled(){return false;}
    public static final String APP_NAME = "GoGoGo";
    public static final String LOG_FILE_NAME = APP_NAME + ".log";
    private static final long MAX_TIME = 1000 * 60 * 60 * 24 * 3; // 3 days

    @Override
    public void onCreate() {
        super.onCreate();

        getSharedPreferences("guidance",0).edit().remove("target").remove("targetName").remove("pendingEnd").remove("endMuted").apply();
        initXlog();
        trackVisibility();

    }

    /**
     * Initialize XLog.
     */
    private void initXlog() {
        File logPath = getExternalFilesDir("Logs");
        if (logPath != null) {
            LogConfiguration config = new LogConfiguration.Builder()
                    .logLevel(LogLevel.ALL)
                    .tag(APP_NAME)                                         // 指定 TAG，默认为 "X-LOG"
                    .enableThreadInfo()                                    // 允许打印线程信息，默认禁止
                    .enableStackTrace(2)                                   // 允许打印深度为 2 的调用栈信息，默认禁止
                    .enableBorder()                                        // 允许打印日志边框，默认禁止
                    .build();

            Printer consolePrinter = new ConsolePrinter();                  // 通过 System.out 打印日志到控制台的打印器
            Printer filePrinter = new FilePrinter                           // 打印日志到文件的打印器
                    .Builder(logPath.getPath())                             // 指定保存日志文件的路径
                    .fileNameGenerator(new ChangelessFileNameGenerator(LOG_FILE_NAME))         // 指定日志文件名生成器，默认为 ChangelessFileNameGenerator("log")
                    .backupStrategy(new NeverBackupStrategy())              // 指定日志文件备份策略，默认为 FileSizeBackupStrategy(1024 * 1024)
                    .cleanStrategy(new FileLastModifiedCleanStrategy(MAX_TIME))     // 指定日志文件清除策略，默认为 NeverCleanStrategy()
                    .build();
            XLog.init(config, consolePrinter, filePrinter);
        }
    }
}