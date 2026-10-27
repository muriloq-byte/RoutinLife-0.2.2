package com.routinlife.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.routinlife.app.R
import com.routinlife.app.data.RoutineRepository
import com.routinlife.app.databinding.ActivityAlarmBinding

/**
 * Tela cheia do alarme. É aberta pela notificação (full-screen intent) mesmo com a tela
 * bloqueada ou apagada, e garante que o serviço de som esteja tocando — inclusive quando o
 * sistema só permite iniciar o serviço a partir de uma tela visível.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RoutineRepository.init(this)
        binding.buttonStop.setOnClickListener {
            AlarmService.stop(this)
            finish()
        }
        bindAlarm(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindAlarm(intent)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun bindAlarm(intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ITEM_ID, -1L)
        val item = RoutineRepository.item(id)
        if (item != null) {
            binding.textAlarmTime.text = item.timeLabel
            binding.textAlarmTitle.text = item.title
            // Garante o som também pelo caminho da tela visível (aqui a partida do serviço
            // é sempre permitida pelo sistema).
            AlarmService.start(this, item)
        } else {
            binding.textAlarmTime.text = getString(R.string.alarm_screen_label)
            binding.textAlarmTitle.text = ""
        }
    }

    companion object {
        /** Ação usada pela notificação para identificar a tela do alarme. */
        const val ACTION_SHOW_ALARM = "com.routinlife.app.action.SHOW_ALARM"
    }
}
