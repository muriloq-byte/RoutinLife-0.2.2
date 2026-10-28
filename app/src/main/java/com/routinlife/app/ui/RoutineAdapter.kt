package com.routinlife.app.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.routinlife.app.R
import com.routinlife.app.data.RoutineItem
import com.routinlife.app.databinding.ItemRoutineRowBinding

/**
 * Linhas da planilha da rotina de UM dia.
 *
 * É um adaptador simples (sem ListAdapter/DiffUtil) porque ele também precisa de lista
 * mutável durante o arraste: [move] troca as posições linha a linha e [currentIds] devolve
 * a ordem final para o fragmento gravar quando o arraste termina.
 *
 * [doneDate] é a data do dia exibido na tela: o risco de "concluído" só aparece para a
 * data exata em que a marca foi feita.
 */
class RoutineAdapter(
    private val onToggle: (RoutineItem, Boolean) -> Unit,
    private val onDone: (RoutineItem, Boolean) -> Unit,
    private val onEdit: (RoutineItem) -> Unit,
    private val onDelete: (RoutineItem) -> Unit
) : RecyclerView.Adapter<RoutineAdapter.RowHolder>() {

    private var items: List<RoutineItem> = emptyList()
    private var doneDate: String = ""
    private var duplicatedTimes: Set<Int> = emptySet()

    /** Troca as linhas exibidas. Marca os horários repetidos para avisar na linha. */
    fun setItems(newItems: List<RoutineItem>, doneDate: String) {
        items = newItems
        this.doneDate = doneDate
        duplicatedTimes = newItems
            .groupingBy { it.hour * 60 + it.minute }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        notifyDataSetChanged()
    }

    /** Move a linha arrastada de [from] para [to]. Retorna false se a posição é inválida. */
    fun move(from: Int, to: Int): Boolean {
        if (from !in items.indices || to !in items.indices || from == to) return false
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        items = mutable
        notifyItemMoved(from, to)
        return true
    }

    /** Ordem atual das linhas (ids), para gravar a reordenação no dia. */
    fun currentIds(): List<Long> = items.map { it.id }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(ItemRoutineRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun getItem(position: Int): RoutineItem = items[position]

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

            // Conclusão presa à data exibida nesta tela.
            val done = item.isDoneOn(doneDate)
            binding.checkDone.setOnCheckedChangeListener(null)
            binding.checkDone.isChecked = done
            binding.checkDone.contentDescription =
                context.getString(R.string.check_done_description, item.title)
            binding.checkDone.setOnCheckedChangeListener { _, checked -> onDone(item, checked) }
            binding.textTitle.paintFlags = if (done) {
                binding.textTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.textTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
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

            // Concluída ou com alarme desativado deixa a linha esmaecida; o item continua na rotina.
            binding.root.alpha = when {
                done -> 0.6f
                !item.enabled -> 0.55f
                else -> 1f
            }
        }
    }
}
