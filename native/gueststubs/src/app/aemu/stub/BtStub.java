package app.aemu.stub;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

import java.lang.reflect.Method;

/**
 * Заглушка службы «bluetooth_manager» для прошивок 4.2+.
 *
 * С ro.kernel.qemu=1 system_server не поднимает BluetoothManagerService («No Bluetooth Service (emulator)»),
 * и BluetoothAdapter.getDefaultAdapter() возвращает null. Код производителей этого не ждёт: блокировка
 * экрана MIUI (connectBLEDevice), службы Broadcom/MediaTek и приложения падают на NullPointerException.
 * Эта служба отвечает на любой вызов IBluetoothManager «пусто»: registerAdapter → null, isEnabled → false,
 * enable → false, getAddress/getName → "". Адаптер при этом существует и сообщает, что Bluetooth выключен.
 *
 * Запускается движком до зиготы: app_process -Djava.class.path=/system/framework/aemu-stubs.jar
 * /system/bin app.aemu.stub.BtStub
 */
public class BtStub extends Binder {
    private static final int FIRST_CALL = 1, LAST_CALL = 0x00ffffff;

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code < FIRST_CALL || code > LAST_CALL) {
            try { return super.onTransact(code, data, reply, flags); } catch (Exception e) { return false; }
        }
        if (reply != null) {
            reply.writeNoException();
            // хватает на любой ответ: boolean/int → 0, строка → "", объект binder → null
            reply.writeInt(0);
            reply.writeInt(0);
        }
        return true;
    }

    @Override
    public String getInterfaceDescriptor() { return "android.bluetooth.IBluetoothManager"; }

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "bluetooth_manager";
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method check = sm.getMethod("checkService", String.class);
        if (check.invoke(null, name) != null) {
            System.out.println("aemu-bt: служба " + name + " уже есть — заглушка не нужна");
            return;
        }
        Method add = sm.getMethod("addService", String.class, IBinder.class);
        add.invoke(null, name, new BtStub());
        System.out.println("aemu-bt: служба " + name + " зарегистрирована");
        // пул потоков binder поднимает app_process; главный поток просто живёт
        Object lock = new Object();
        synchronized (lock) { while (true) lock.wait(); }
    }
}
