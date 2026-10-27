package com.routinlife.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.routinlife.app.data.RoutineRepository

/**
 * Recebe o disparo do alarme (ação FIRE_ALARM) e o pedido de parar (ação STOP_ALARM,
 * vindo do botão PARAR da notificação).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        // O processo pode ter sido encerrado pelo sistema: o repositório é inicializado aqui
        // para que a rotina seja lida do armazenamento local.
        RoutineRepository.init(appContext)

        when (intent.action) {
            AlarmScheduler.ACTION_STOP_ALARM -> AlarmService.stop(appContext)
            AlarmScheduler.ACTION_FIRE_ALARM -> fireAlarm(appContext, intent)
            else -> Unit
        }
    }

    private fun fireAlarm(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ITEM_ID, -1L)
        val item = RoutineRepository.item(id) ?: return

        if (!item.enabled) {
            // Alarme desativado depois de agendado: garante que nada fique pendente.
            AlarmScheduler.cancel(context, item)
            return
        }

        // Reagenda ANTES de tocar: mesmo que o processo seja morto agora, a rotina já está
        // agendada para amanhã (é isso que faz a rotina repetir todos os dias).
        AlarmScheduler.schedule(context, item)

        val started = AlarmService.start(context, item)
        if (!started) {
            // Serviço em primeiro plano bloqueado: garante o aviso sonoro pela notificação.
            AlarmNotifier.notifyFallback(context, listOf(item))
        }
        RoutineRepository.reload()
    }
}
