package com.routinlife.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.routinlife.app.data.RoutineRepository

/**
 * Depois de reiniciar o aparelho (ou trocar o relógio/fuso), todos os alarmes agendados no
 * AlarmManager são apagados pelo sistema. Este receiver relê a rotina salva localmente e
 * reagenda tudo que estiver ativo.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val appContext = context.applicationContext
                RoutineRepository.init(appContext)
                AlarmNotifier.createChannels(appContext)
                AlarmScheduler.scheduleAll(appContext)
            }

            else -> Unit
        }
    }
}
