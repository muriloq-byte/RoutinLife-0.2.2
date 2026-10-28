package com.routinlife.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.routinlife.app.R
import com.routinlife.app.alarm.AlarmScheduler
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineRepository
import com.routinlife.app.data.WeekDays
import com.routinlife.app.databinding.FragmentMontarRotinaBinding
import java.util.Calendar

/**
 * Aba 1: escolher DIA da semana + horário + nome da atividade. Cada item criado já sai com
 * o alarme agendado para o dia escolhido nos chips da semana (e repetindo toda semana).
 */
class MontarRotinaFragment : Fragment() {

    private var _binding: FragmentMontarRotinaBinding? = null
    private val binding get() = _binding!!

    private var selectedHour = 8
    private var selectedMinute = 0
    private var selectedDay = WeekDays.currentDay()
    private var lastItems: List<RoutineItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMontarRotinaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        RoutineRepository.init(requireContext())

        selectedDay = (activity as? MainActivity)?.selectedDay ?: WeekDays.currentDay()
        DayChips.bind(binding.chipDays, selectedDay) { day -> onDayChanged(day) }

        val now = Calendar.getInstance()
        var hour = now.get(Calendar.HOUR_OF_DAY)
        var minute = if (now.get(Calendar.MINUTE) < 30) 30 else 0
        if (minute == 0) {
            hour = (hour + 1) % 24
        }
        selectedHour = hour
        selectedMinute = minute

        updateTimeLabel()
        binding.buttonTime.setOnClickListener { showTimePicker() }
        binding.buttonAdd.setOnClickListener { addItem() }
        RoutineRepository.items.observe(viewLifecycleOwner) {
            lastItems = it
            renderSchedule()
        }
    }

    /** Dia trocado nos chips: re-renderiza a lista deste dia (os dados dos outros ficam guardados). */
    private fun onDayChanged(day: Int) {
        selectedDay = day
        (activity as? MainActivity)?.selectedDay = day
        renderSchedule()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText(R.string.build_time_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            selectedHour = picker.hour
            selectedMinute = picker.minute
            updateTimeLabel()
        }
        picker.show(parentFragmentManager, "routinlife_time_picker")
    }

    private fun updateTimeLabel() {
        binding.buttonTime.text = formatTime(selectedHour, selectedMinute)
    }

    private fun addItem() {
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.inputLayoutTitle.error = getString(R.string.build_error_empty_name)
            return
        }
        binding.inputLayoutTitle.error = null

        val sameTime = RoutineRepository.itemsAtSameTime(selectedDay, selectedHour, selectedMinute)
        if (sameTime.isEmpty()) {
            saveItem(title, duplicate = false)
        } else {
            confirmSameTime(sameTime) { saveItem(title, duplicate = true) }
        }
    }

    private fun saveItem(title: String, duplicate: Boolean) {
        val item = RoutineRepository.add(title, selectedHour, selectedMinute, selectedDay)
        AlarmScheduler.schedule(requireContext(), item)

        binding.inputTitle.text?.clear()
        val message = getString(
            if (duplicate) R.string.build_added_duplicate_success else R.string.build_added_success,
            item.timeLabel,
            getString(WeekDays.shortNameRes(item.day))
        )
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    /** Tratamento de dois itens no mesmo horário: avisa e deixa o usuário decidir. */
    private fun confirmSameTime(duplicates: List<RoutineItem>, onConfirm: () -> Unit) {
        val names = duplicates.joinToString(separator = "\n") { "• ${it.timeLabel} — ${it.title}" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.duplicate_title)
            .setMessage(
                getString(
                    R.string.duplicate_message,
                    duplicates.size,
                    formatTime(selectedHour, selectedMinute),
                    names
                )
            )
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.duplicate_confirm) { _, _ -> onConfirm() }
            .show()
    }

    /** Renderiza SOMENTE as rotinas do dia selecionado, na ordem das linhas do dia. */
    private fun renderSchedule() {
        val items = WeekDays.forDay(lastItems, selectedDay)
        val dayName = getString(WeekDays.nameRes(selectedDay))
        binding.textScheduleTitle.text = getString(R.string.build_current_title, dayName)
        val text = if (items.isEmpty()) {
            getString(R.string.build_current_empty_day, dayName)
        } else {
            items.joinToString(separator = "\n") { item ->
                val marker = if (item.enabled) "" else "  ${getString(R.string.build_disabled_marker)}"
                val done = if (item.isDoneOn(WeekDays.dateOf(selectedDay)))
                    "  ${getString(R.string.build_done_marker)}" else ""
                String.format("%-6s %s%s%s", item.timeLabel, item.title, marker, done)
            }
        }
        binding.textSchedule.text = text
    }
}
