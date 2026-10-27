package com.routinlife.app

import android.app.Application
import com.routinlife.app.alarm.AlarmNotifier
import com.routinlife.app.alarm.AlarmScheduler
import com.routinlife.app.data.RoutineRepository

class RoutinLifeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RoutineRepository.init(this)
        AlarmNotifier.createChannels(this)
        // Rede de segurança: a cada abertura do app todos os alarmes ativos são reagendados,
        // além do reagendamento feito no boot (BootReceiver).
        AlarmScheduler.scheduleAll(this)
    }
}
