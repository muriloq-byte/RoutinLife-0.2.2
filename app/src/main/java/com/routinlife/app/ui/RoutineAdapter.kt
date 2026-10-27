package com.routinlife.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.routinlife.app.R
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.databinding.ItemRoutineRowBinding

/** Linhas da planilha da rotina. */
class RoutineAdapter(
    private val onToggle: (RoutineItem, Boolean) -> Unit,
    private val onEdit: (RoutineItem) -> Unit,
    private val onDelete: (RoutineItem) -> Unit
) : ListAdapter<RoutineItem, RoutineAdapter.RowHolder>(DIFF) {

    private var duplicatedTimes: Set<Int> = emptySet()

    /** Marca os horários repetidos para avisar na linha (dois itens no mesmo horário). */
    fun submit(items: List<RoutineItem>) {
        duplicatedTimes = items
            .groupingBy { it.hour * 60 + it.minute }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(
            ItemRoutineRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RowHolder(private val binding: ItemRoutineRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RoutineItem) {
            val context = binding.root.context
            binding.textTime.text = item.timeLabel
            binding.textTitle.text = item.title

            val isDuplicated = duplicatedTimes.contains(item.hour * 60 + item.minute)
            binding.textWarning.visibility = if (isDuplicated) View.VISIBLE else View.GONE
            if (isDuplicated) {
                binding.textWarning.text = context.getString(R.string.my_same_time_warning)
            }

            binding.switchAlarm.setOnCheckedChangeListener(null)
            binding.switchAlarm.isChecked = item.enabled
            binding.switchAlarm.contentDescription =
                context.getString(R.string.my_alarm_switch_description, item.title)
            binding.switchAlarm.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }

            binding.buttonEdit.contentDescription =
                context.getString(R.string.my_edit_button_description)
            binding.buttonEdit.setOnClickListener { onEdit(item) }

            binding.buttonDelete.contentDescription =
                context.getString(R.string.my_delete_button_description)
            binding.buttonDelete.setOnClickListener { onDelete(item) }

            // Alarme desativado deixa a linha esmaecida, mas o item continua na rotina.
            binding.root.alpha = if (item.enabled) 1f else 0.55f
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<RoutineItem>() {
            override fun areItemsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: RoutineItem, newItem: RoutineItem): Boolean =
                oldItem == newItem
        }
    }
}
