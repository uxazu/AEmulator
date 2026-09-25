package dev.lk.m7sense;

import android.view.Surface;

/**
 * JNI-прослойка к libglbridge.so (GL-мост движка KK). Имя пакета и сигнатуры фиксированы
 * экспортами библиотеки: Java_dev_lk_m7sense_GlBridge_*.
 * Мост принимает поток GLES-команд гостя по unix-сокету и исполняет их на GPU телефона,
 * рисуя прямо в Surface приложения.
 */
public final class GlBridge {
    public static final GlBridge INSTANCE = new GlBridge();
    private static volatile boolean loaded;
    private static volatile boolean started;

    private GlBridge() {}

    private static native long nativeFrames();
    private native boolean nativeStart(Surface surface, String sock, String log, int w, int h, int verbose);
    private static native void nativeSurface(Surface surface);

    public static synchronized boolean load() {
        if (loaded) return true;
        try {
            System.loadLibrary("glbridge");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
        }
        return loaded;
    }

    public static boolean running() { return started; }

    public static synchronized boolean start(Surface surface, String sock, String log, int w, int h, boolean verbose) {
        if (started) return true;
        if (!load()) return false;
        started = INSTANCE.nativeStart(surface, sock, log, w, h, verbose ? 1 : 0);
        return started;
    }

    public static void surface(Surface s) {
        if (!started) return;
        try { nativeSurface(s); } catch (Throwable ignored) {}
    }

    public static long frames() {
        if (!started) return 0;
        try { return nativeFrames(); } catch (Throwable t) { return 0; }
    }
}
