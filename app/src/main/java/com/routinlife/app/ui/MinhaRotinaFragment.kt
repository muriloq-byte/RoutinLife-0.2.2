package com.routinlife.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.routinlife.app.R
import com.routinlife.app.alarm.AlarmScheduler
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineRepository
import com.routinlife.app.databinding.DialogEditItemBinding
import com.routinlife.app.databinding.FragmentMinhaRotinaBinding

/** Aba 2: a rotina em formato de planilha, em ordem cronológica, com editar/excluir/ativar. */
class MinhaRotinaFragment : Fragment() {

    private var _binding: FragmentMinhaRotinaBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RoutineAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMinhaRotinaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        RoutineRepository.init(requireContext())

        adapter = RoutineAdapter(
            onToggle = ::toggleAlarm,
            onEdit = ::showEditDialog,
            onDelete = ::confirmDelete
        )

        binding.recyclerRoutine.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRoutine.adapter = adapter
        ContextCompat.getDrawable(requireContext(), R.drawable.divider_row)?.let { divider ->
            binding.recyclerRoutine.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
                    setDrawable(divider)
                }
            )
        }

        binding.buttonPermissions.setOnClickListener {
            (activity as? MainActivity)?.requestMissingPermissions()
        }

        RoutineRepository.items.observe(viewLifecycleOwner) { items ->
            adapter.submit(items)
            binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionBanner()
        RoutineRepository.init(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshPermissionBanner() {
        val currentBinding = _binding ?: return
        val context = context ?: return
        val missingPermissions =
            !AlarmScheduler.isExactAlarmAllowed(context) ||
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
        currentBinding.cardPermissions.visibility = if (missingPermissions) View.VISIBLE else View.GONE
    }

    private fun toggleAlarm(item: RoutineItem, enabled: Boolean) {
        RoutineRepository.setEnabled(item.id, enabled)
        val updated = RoutineRepository.item(item.id) ?: item
        if (enabled) {
            AlarmScheduler.schedule(requireContext(), updated)
            Snackbar.make(
                binding.root,
                getString(R.string.my_alarm_enabled, updated.timeLabel, updated.title),
                Snackbar.LENGTH_SHORT
            ).show()
        } else {
            AlarmScheduler.cancel(requireContext(), updated)
            Snackbar.make(
                binding.root,
                getString(R.string.my_alarm_disabled, updated.title),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDelete(item: RoutineItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_delete_title)
            .setMessage(getString(R.string.my_delete_message, item.title, item.timeLabel))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.my_delete_confirm) { _, _ ->
                AlarmScheduler.cancel(requireContext(), item)
                RoutineRepository.remove(item.id)
                Snackbar.make(
                    binding.root,
                    getString(R.string.my_deleted, item.title),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    private fun showEditDialog(item: RoutineItem) {
        val dialogBinding = DialogEditItemBinding.inflate(layoutInflater)
        var hour = item.hour
        var minute = item.minute

        dialogBinding.buttonEditTime.text = formatTime(hour, minute)
        dialogBinding.buttonEditTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(hour)
                .setMinute(minute)
                .setTitleText(R.string.build_time_picker_title)
                .build()
            picker.addOnPositiveButtonClickListener {
                hour = picker.hour
                minute = picker.minute
                dialogBinding.buttonEditTime.text = formatTime(hour, minute)
            }
            picker.show(parentFragmentManager, "routinlife_edit_time_picker")
        }
        dialogBinding.inputEditTitle.setText(item.title)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.my_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newTitle = dialogBinding.inputEditTitle.text?.toString()?.trim().orEmpty()
                if (newTitle.isEmpty()) {
                    dialogBinding.inputEditLayout.error = getString(R.string.build_error_empty_name)
                    return@setOnClickListener
                }
                dialogBinding.inputEditLayout.error = null

                val sameTime =
                    RoutineRepository.itemsAtSameTime(hour, minute, ignoreId = item.id)
                if (sameTime.isEmpty()) {
                    saveEdit(item, newTitle, hour, minute)
                    dialog.dismiss()
                } else {
                    val names = sameTime.joinToString(separator = "\n") {
                        "• ${it.timeLabel} — ${it.title}"
                    }
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.duplicate_title)
                        .setMessage(
                            getString(
                                R.string.duplicate_message,
                                sameTime.size,
                                formatTime(hour, minute),
                                names
                            )
                        )
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.duplicate_confirm) { _, _ ->
                            saveEdit(item, newTitle, hour, minute)
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
        dialog.show()
    }

    private fun saveEdit(item: RoutineItem, title: String, hour: Int, minute: Int) {
        val updated = item.copy(title = title, hour = hour, minute = minute)
        RoutineRepository.update(updated)
        if (updated.enabled) {
            AlarmScheduler.schedule(requireContext(), updated)
        } else {
            AlarmScheduler.cancel(requireContext(), updated)
        }
        Snackbar.make(
            binding.root,
            getString(R.string.my_updated, updated.timeLabel),
            Snackbar.LENGTH_SHORT
        ).show()
    }
}
