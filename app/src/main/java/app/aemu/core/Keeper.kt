package app.aemu.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.aemu.R
import app.aemu.ui.VmActivity

/**
 * Служба переднего плана процесса :vm. Пока она жива, Android не выгружает процесс
 * с прошивкой при сворачивании и не режет ему процессор.
 */
class Keeper : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val title = i?.getStringExtra("title") ?: "Android"
        runCatching { startForeground(ID, note(this, title)) }
        if (i?.action == STOP) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CH = "vm"
        private const val ID = 1
        const val STOP = "app.aemu.KEEPER_STOP"

        fun ensureChannel(ctx: Context) {
            runCatching {
                ctx.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(NotificationChannel(CH, ctx.getString(R.string.channel_vm), NotificationManager.IMPORTANCE_LOW))
            }
        }

        fun note(ctx: Context, title: String): Notification {
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, VmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(ctx, CH)
                .setContentTitle(ctx.getString(R.string.vm_running, title))
                .setContentText(ctx.getString(R.string.vm_running_hint))
                .setSmallIcon(R.drawable.ic_stat_vm)
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }

        fun hold(ctx: Context, title: String) {
            runCatching { ctx.startForegroundService(Intent(ctx, Keeper::class.java).putExtra("title", title)) }
        }

        fun release(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, Keeper::class.java)) }
        }
    }
}
