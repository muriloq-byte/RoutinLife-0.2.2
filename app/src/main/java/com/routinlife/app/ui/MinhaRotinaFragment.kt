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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.routinlife.app.R
import com.routinlife.app.alarm.AlarmScheduler
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineRepository
import com.routinlife.app.data.WeekDays
import com.routinlife.app.databinding.DialogEditItemBinding
import com.routinlife.app.databinding.FragmentMinhaRotinaBinding

/**
 * Aba 2: a rotina do DIA selecionado em formato de planilha, na ordem das linhas, com
 * editar/excluir/ativar alarme, marcar como concluída e reordenar arrastando a linha.
 * Os chips da semana trocam o dia; os dados dos outros dias não são tocados.
 */
class MinhaRotinaFragment : Fragment() {

    private var _binding: FragmentMinhaRotinaBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RoutineAdapter
    private var selectedDay = WeekDays.currentDay()
    private var lastItems: List<RoutineItem> = emptyList()

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

        selectedDay = (activity as? MainActivity)?.selectedDay ?: WeekDays.currentDay()
        DayChips.bind(binding.chipDays, selectedDay) { day -> onDayChanged(day) }

        adapter = RoutineAdapter(
            onToggle = ::toggleAlarm,
            onDone = ::toggleDone,
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

        // Reordenar: segurar e arrastar a linha para cima/baixo dentro do dia exibido.
        val dragCallback = object :
            ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean =
                adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                // Arraste terminou: grava a nova ordem APENAS do dia exibido.
                RoutineRepository.reorder(selectedDay, adapter.currentIds())
            }
        }
        ItemTouchHelper(dragCallback).attachToRecyclerView(binding.recyclerRoutine)

        RoutineRepository.items.observe(viewLifecycleOwner) { items ->
            lastItems = items
            renderDay()
        }
    }

    /** Dia trocado nos chips: reexibe somente as rotinas do novo dia. */
    private fun onDayChanged(day: Int) {
        selectedDay = day
        (activity as? MainActivity)?.selectedDay = day
        renderDay()
    }

    /** Renderiza as rotinas do dia selecionado e o estado de conclusão da data dele. */
    private fun renderDay() {
        val dayItems = WeekDays.forDay(lastItems, selectedDay)
        adapter.setItems(dayItems, WeekDays.dateOf(selectedDay))
        binding.textEmpty.text =
            getString(R.string.my_empty_day, getString(WeekDays.nameRes(selectedDay)))
        binding.textEmpty.visibility = if (dayItems.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Marca/desmarca a conclusão presa à data do dia exibido (não afeta outros dias). */
    private fun toggleDone(item: RoutineItem, done: Boolean) {
        RoutineRepository.setDone(item.id, if (done) WeekDays.dateOf(selectedDay) else null)
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

                val sameTime = RoutineRepository.itemsAtSameTime(
                    selectedDay, hour, minute, ignoreId = item.id
                )
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
