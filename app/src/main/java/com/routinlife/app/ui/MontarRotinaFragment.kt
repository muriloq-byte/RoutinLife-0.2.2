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
import com.routinlife.app.databinding.FragmentMontarRotinaBinding
import java.util.Calendar

/** Aba 1: escolher horário + nome da atividade. Cada item criado já sai com o alarme agendado. */
class MontarRotinaFragment : Fragment() {

    private var _binding: FragmentMontarRotinaBinding? = null
    private val binding get() = _binding!!

    private var selectedHour = 8
    private var selectedMinute = 0

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
        RoutineRepository.items.observe(viewLifecycleOwner) { renderSchedule(it) }
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

        val sameTime = RoutineRepository.itemsAtSameTime(selectedHour, selectedMinute)
        if (sameTime.isEmpty()) {
            saveItem(title, duplicate = false)
        } else {
            confirmSameTime(sameTime) { saveItem(title, duplicate = true) }
        }
    }

    private fun saveItem(title: String, duplicate: Boolean) {
        val item = RoutineRepository.add(title, selectedHour, selectedMinute)
        AlarmScheduler.schedule(requireContext(), item)

        binding.inputTitle.text?.clear()
        val message = getString(
            if (duplicate) R.string.build_added_duplicate_success else R.string.build_added_success,
            item.timeLabel
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

    private fun renderSchedule(items: List<RoutineItem>) {
        val text = if (items.isEmpty()) {
            getString(R.string.build_current_empty)
        } else {
            items.joinToString(separator = "\n") { item ->
                val marker = if (item.enabled) "" else "  ${getString(R.string.build_disabled_marker)}"
                String.format("%-6s %s%s", item.timeLabel, item.title, marker)
            }
        }
        binding.textSchedule.text = text
    }
}
