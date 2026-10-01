package app.aemu.stub;

import java.lang.reflect.InvocationTargetException;

/**
 * Runs a command-line tool of the firmware (am, pm, input, svc, content, settings…) and exits.
 *
 * Under qemu the tools finish their work but never quit: when main() returns, the VM waits for its non-daemon
 * threads, and the binder thread stays blocked reading binderd's socket. Every "am start", "pm install" or "input
 * text" from the emulator menu then hung until its timeout. System.exit() ends the process right away.
 *
 * app_process /system/bin app.aemu.stub.Run com.android.commands.am.Am start …
 */
public final class Run {
    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            System.err.println("usage: Run <main class> [args...]");
            System.exit(2);
        }
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        int code = 0;
        try {
            Class.forName(args[0]).getMethod("main", String[].class).invoke(null, (Object) rest);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            c.printStackTrace();
            code = 1;
        }
        System.out.flush();
        System.err.flush();
        System.exit(code);
    }
}
