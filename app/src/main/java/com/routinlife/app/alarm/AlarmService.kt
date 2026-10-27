package com.routinlife.app.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineRepository

/**
 * Serviço em primeiro plano que toca o som do alarme em loop e mantém a vibração.
 *
 * Decisão de arquitetura: um app targeting Android 12+ não pode iniciar serviços em primeiro
 * plano livremente em segundo plano, mas um disparo de alarme exato é uma das exceções
 * documentadas — é justamente o caso de um app de rotina/alarme. O serviço é promovido com
 * startForeground assim que começa, com o tipo mediaPlayback (o que exige apenas a permissão
 * FOREGROUND_SERVICE_MEDIA_PLAYBACK no manifesto). Se o sistema recusar a promoção, o
 * AlarmNotifier publica o aviso sonoro como plano B.
 *
 * Dois itens no MESMO horário: os dois disparam quase juntos e o serviço mantém a lista de
 * alarmes ativos, mostrando todos no mesmo aviso — o som toca uma vez só, sem sobreposição.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            silenceAndStop()
            return START_NOT_STICKY
        }

        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ITEM_ID, -1L) ?: -1L
        RoutineRepository.item(id)?.let { activeItems[it.id] = it }

        if (activeItems.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                AlarmNotifier.ALARM_NOTIFICATION_ID,
                AlarmNotifier.ringingNotification(this, activeItems.values.toList())
            )
        } catch (e: Exception) {
            AlarmNotifier.notifyFallback(this, activeItems.values.toList())
        }

        startRinging()
        return START_STICKY
    }

    private fun startRinging() {
        if (player == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(applicationContext, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                null
            }
        }

        if (vibrator == null) {
            vibrator = defaultVibrator()?.also { device ->
                val pattern = longArrayOf(0, 800, 500, 800, 500)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        device.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        device.vibrate(pattern, 0)
                    }
                } catch (e: Exception) {
                    // Aparelho sem vibração disponível.
                }
            }
        }

        // O som toca em loop, mas para sozinho depois de um minuto (a notificação continua
        // disponível com o botão PARAR).
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ silenceAndStop() }, AUTO_SILENCE_MS)
    }

    private fun silenceAndStop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { mediaPlayer ->
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
            } catch (e: Exception) {
                // Ignora: o player pode já ter sido liberado.
            }
            mediaPlayer.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        activeItems.clear()
        AlarmNotifier.cancel(this)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // O serviço pode não estar em primeiro plano.
        }
        stopSelf()
    }

    private fun defaultVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.routinlife.app.action.START_ALARM"
        const val ACTION_STOP = "com.routinlife.app.action.STOP_ALARM"
        private const val AUTO_SILENCE_MS = 60_000L

        /** Alarmes tocando agora (id -> item). Compartilhado dentro do processo. */
        private val activeItems = LinkedHashMap<Long, RoutineItem>()

        /** Inicia (ou soma a) o alarme tocando. Retorna false se o serviço não pôde começar. */
        fun start(context: Context, item: RoutineItem): Boolean {
            activeItems[item.id] = item
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ITEM_ID, item.id)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: Exception) {
                activeItems.remove(item.id)
                false
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).setAction(ACTION_STOP)
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Serviço não pôde ser iniciado: o cancelamento do aviso abaixo basta.
            }
            activeItems.clear()
            AlarmNotifier.cancel(context)
        }
    }
}
