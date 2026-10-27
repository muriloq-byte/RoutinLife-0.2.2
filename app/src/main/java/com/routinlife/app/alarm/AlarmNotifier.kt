package com.routinlife.app.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.routinlife.app.R
import com.routinlife.app.data.RoutineItem

/**
 * Notificações do alarme: canal de alta prioridade com som de alarme do sistema e a
 * notificação em tela cheia (full-screen intent) que abre a AlarmActivity por cima do
 * bloqueio de tela.
 */
object AlarmNotifier {

    const val CHANNEL_ALARM = "alarme_rotina"
    const val ALARM_NOTIFICATION_ID = 4701
    private const val REQUEST_SHOW_ALARM = 9911

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 500, 800, 500)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(alarmChannel)
    }

    /** Notificação do alarme tocando: tela cheia + botão PARAR. */
    fun ringingNotification(context: Context, items: List<RoutineItem>): Notification {
        val first = items.first()
        val title = if (items.size == 1) {
            first.title
        } else {
            context.getString(R.string.notification_alarm_multiple, items.size)
        }
        val details = items.joinToString(separator = "\n") { "${it.timeLabel}  ${it.title}" }
        val contentIntent = showAlarmIntent(context, first)
        val stopIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmScheduler.ACTION_STOP_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title)
            .setContentText(details.replace("\n", " • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(0, context.getString(R.string.action_stop), stopIntent)
            .build()
    }

    /**
     * Plano B: quando o serviço em primeiro plano não pode ser iniciado (o sistema bloqueia a
     * partida em segundo plano em algumas situações), o aviso é publicado na hora com o som do
     * canal de alarme, então o alarme nunca passa em branco.
     */
    @SuppressLint("MissingPermission")
    fun notifyFallback(context: Context, items: List<RoutineItem>) {
        try {
            NotificationManagerCompat.from(context)
                .notify(ALARM_NOTIFICATION_ID, ringingNotification(context, items))
        } catch (e: Exception) {
            // Sem permissão de notificação: o som continua sendo tocado pelo serviço.
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Nada a fazer.
        }
    }

    private fun showAlarmIntent(context: Context, item: RoutineItem): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .setAction(AlarmActivity.ACTION_SHOW_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AlarmScheduler.EXTRA_ITEM_ID, item.id)
        return PendingIntent.getActivity(
            context,
            REQUEST_SHOW_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
