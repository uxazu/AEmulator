package dev.lk.dhd;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * JNI-прослойка к libdhdslot.so (движок GB): binderd и glserverd работают не отдельными
 * исполняемыми файлами, а внутри служб приложения в своих процессах (:binder, :gl).
 * Так Android не считает их «фантомными» процессами и не убивает.
 */
public abstract class Slot extends Service {
    private boolean started;

    public static native int runNative(String which, String[] argv, String[] env, String log);

    protected abstract String which();

    @Override
    public void onCreate() {
        super.onCreate();
        try { System.loadLibrary("dhdslot"); } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (started) return START_NOT_STICKY;
        started = true;
        final String[] argv = i != null && i.getStringArrayExtra("argv") != null ? i.getStringArrayExtra("argv") : new String[0];
        final String[] env = i != null && i.getStringArrayExtra("env") != null ? i.getStringArrayExtra("env") : new String[0];
        final String log = i != null ? i.getStringExtra("log") : null;
        Thread t = new Thread(() -> {
            try { runNative(which(), argv, env, log); } catch (Throwable ignored) {}
            stopSelf();
            android.os.Process.killProcess(android.os.Process.myPid());
        }, which());
        t.start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    public static boolean start(Context ctx, Class<? extends Slot> cls, List<String> argv, Map<String, String> env, File log) {
        String[] e = new String[env.size() * 2];
        int k = 0;
        for (Map.Entry<String, String> it : env.entrySet()) { e[k++] = it.getKey(); e[k++] = it.getValue(); }
        Intent i = new Intent(ctx, cls)
                .putExtra("argv", argv.toArray(new String[0]))
                .putExtra("env", e)
                .putExtra("log", log.getAbsolutePath());
        try { return ctx.startService(i) != null; } catch (Throwable t) { return false; }
    }
}
