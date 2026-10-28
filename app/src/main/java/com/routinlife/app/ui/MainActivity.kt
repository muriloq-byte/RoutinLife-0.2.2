package com.routinlife.app.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.routinlife.app.R
import com.routinlife.app.alarm.AlarmNotifier
import com.routinlife.app.data.RoutineRepository
import com.routinlife.app.data.WeekDays
import com.routinlife.app.databinding.ActivityMainBinding

/**
 * Tela principal: duas abas ("Montar Rotina" e "Minha Rotina"), a semana com os 7 dias
 * (dia selecionado compartilhado entre as abas) e o fluxo de permissões explicado ao
 * usuário no primeiro uso.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var askExactAlarmAfterNotification = false

    /**
     * Dia da semana selecionado, compartilhado pelas duas abas: montar em segunda e
     * conferir em segunda mostram o mesmo dia. Começa em hoje e sobrevive à rotação
     * (restaurado de savedInstanceState antes de as abas criarem a view).
     */
    var selectedDay: Int = WeekDays.currentDay()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, R.string.perm_notifications_denied, Toast.LENGTH_LONG).show()
            }
            if (askExactAlarmAfterNotification) {
                askExactAlarmAfterNotification = false
                askForExactAlarm()
            }
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Ao voltar das configurações a aba "Minha Rotina" revalida o aviso de permissões.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedDay = savedInstanceState?.getInt(KEY_SELECTED_DAY) ?: WeekDays.currentDay()
        RoutineRepository.init(this)
        AlarmNotifier.createChannels(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.tab_build -> {
                    showTab(TAG_BUILD)
                    true
                }

                R.id.tab_my -> {
                    showTab(TAG_MY)
                    true
                }

                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.tab_build
            showTab(TAG_BUILD)
        }

        runOnboardingIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_DAY, selectedDay)
    }

    private fun showTab(tag: String) {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current != null && (current.tag == tag)) return
        val fragment = if (tag == TAG_BUILD) MontarRotinaFragment() else MinhaRotinaFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }

    // ------------------------------------------------------------------ permissões

    private fun runOnboardingIfNeeded() {
        val prefs = getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false)) return
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_notifications_title)
            .setMessage(R.string.perm_notifications_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_allow) { _, _ -> requestNotifications(thenAskExactAlarm = true) }
            .setNegativeButton(R.string.perm_later) { _, _ -> askForExactAlarm() }
            .show()
    }

    /** Usado pelo aviso da aba "Minha Rotina" quando falta alguma permissão. */
    fun requestMissingPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsEnabled()) {
            requestNotifications(thenAskExactAlarm = true)
            return
        }
        if (!exactAlarmsAllowed()) {
            askForExactAlarm()
            return
        }
        if (!fullScreenIntentAllowed()) {
            askForFullScreenIntent()
            return
        }
        Toast.makeText(this, R.string.perm_all_set, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotifications(thenAskExactAlarm: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askExactAlarmAfterNotification = thenAskExactAlarm
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (thenAskExactAlarm) {
            askForExactAlarm()
        }
    }

    private fun askForExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || exactAlarmsAllowed()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_exact_title)
            .setMessage(R.string.perm_exact_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_allow) { _, _ -> openExactAlarmSettings() }
            .setNegativeButton(R.string.perm_later, null)
            .show()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", packageName, null)
        )
        launchSettings(intent)
    }

    private fun askForFullScreenIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.perm_fsi_title)
            .setMessage(R.string.perm_fsi_message)
            .setCancelable(false)
            .setPositiveButton(R.string.perm_allow) { _, _ ->
                launchSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri()))
            }
            .setNegativeButton(R.string.perm_later, null)
            .show()
    }

    private fun launchSettings(intent: Intent) {
        try {
            settingsLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.perm_exact_settings_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun packageUri(): Uri = Uri.fromParts("package", packageName, null)

    private fun notificationsEnabled(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun exactAlarmsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return true
        return manager.canScheduleExactAlarms()
    }

    private fun fullScreenIntentAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    private companion object {
        const val TAG_BUILD = "tab_build"
        const val TAG_MY = "tab_my"
        const val KEY_SELECTED_DAY = "selected_week_day"
        const val PREFS_ONBOARDING = "routinlife_onboarding"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
