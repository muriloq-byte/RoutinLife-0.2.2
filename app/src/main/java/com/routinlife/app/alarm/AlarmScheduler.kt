package com.routinlife.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineRepository
import java.util.Calendar

/**
 * Agenda os alarmes no AlarmManager.
 *
 * Decisão de arquitetura:
 * - Um PendingIntent por item (requestCode derivado do id), com ação própria, para poder
 *   cancelar/reagendar item por item sem afetar os outros.
 * - Alarmes NÃO são repetitivos do tipo setRepeating (no Android 4.4+ eles deixam de ser
 *   exatos). Em vez disso, agendamos sempre a PRÓXIMA ocorrência exata e o próprio
 *   AlarmReceiver reagenda o dia seguinte assim que o alarme toca. Assim a rotina é
 *   diária, sobrevive a mudanças de horário do aparelho e nunca fica "presa" no passado.
 * - setExactAndAllowWhileIdle quando o app tem permissão de alarme exato; setAlarmClock como
 *   primeira alternativa (é exato e não depende da permissão) e set() como último recurso.
 */
object AlarmScheduler {

    const val ACTION_FIRE_ALARM = "com.routinlife.app.action.FIRE_ALARM"
    const val ACTION_STOP_ALARM = "com.routinlife.app.action.STOP_ALARM"
    const val EXTRA_ITEM_ID = "extra_item_id"

    /** Reagenda (ou cancela) tudo que existe na rotina. Usado no boot e na abertura do app. */
    fun scheduleAll(context: Context) {
        RoutineRepository.all().forEach { item ->
            if (item.enabled) schedule(context, item) else cancel(context, item)
        }
    }

    fun schedule(context: Context, item: RoutineItem) {
        if (!item.enabled) {
            cancel(context, item)
            return
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAt = nextTriggerMillis(item)
        val pendingIntent = pendingIntent(context, item)

        if (canScheduleExact(manager)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                return
            } catch (e: SecurityException) {
                // Permissão revogada entre a verificação e a chamada: cai para o plano B.
            }
        }
        try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, null), pendingIntent)
            return
        } catch (e: SecurityException) {
            // Nem alarme-relógio foi permitido: último recurso abaixo.
        }
        manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, item: RoutineItem) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(item),
            fireIntent(context, item),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** Próxima ocorrência do horário do item: hoje se ainda não passou, senão amanhã. */
    fun nextTriggerMillis(item: RoutineItem, from: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= from) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    fun isExactAlarmAllowed(context: Context): Boolean {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
        return canScheduleExact(manager)
    }

    private fun fireIntent(context: Context, item: RoutineItem): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE_ALARM)
            .putExtra(EXTRA_ITEM_ID, item.id)

    private fun pendingIntent(context: Context, item: RoutineItem): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(item),
            fireIntent(context, item),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(item: RoutineItem): Int =
        (item.id xor (item.id ushr 32)).toInt() and 0x7FFFFFFF
}
