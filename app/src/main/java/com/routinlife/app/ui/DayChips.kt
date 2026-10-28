package com.routinlife.app.ui

import android.view.View
import com.google.android.material.chip.ChipGroup
import com.routinlife.app.R
import com.routinlife.app.data.WeekDays
import java.util.Calendar

/**
 * Liga os 7 chips da semana (Seg ... Dom) às constantes de dia usadas pelos dados.
 * Compartilhado pelas duas abas para que o dia selecionado seja sempre o mesmo.
 */
object DayChips {

    fun chipIdFor(day: Int): Int = when (day) {
        Calendar.MONDAY -> R.id.chipMonday
        Calendar.TUESDAY -> R.id.chipTuesday
        Calendar.WEDNESDAY -> R.id.chipWednesday
        Calendar.THURSDAY -> R.id.chipThursday
        Calendar.FRIDAY -> R.id.chipFriday
        Calendar.SATURDAY -> R.id.chipSaturday
        else -> R.id.chipSunday
    }

    fun dayFor(chipId: Int): Int = when (chipId) {
        R.id.chipMonday -> Calendar.MONDAY
        R.id.chipTuesday -> Calendar.TUESDAY
        R.id.chipWednesday -> Calendar.WEDNESDAY
        R.id.chipThursday -> Calendar.THURSDAY
        R.id.chipFriday -> Calendar.FRIDAY
        R.id.chipSaturday -> Calendar.SATURDAY
        R.id.chipSunday -> Calendar.SUNDAY
        else -> WeekDays.currentDay()
    }

    /**
     * Marca o dia selecionado (sem disparar o callback) e assina as mudanças do grupo.
     * O grupo tem seleção única: escolher um dia desmarca os outros.
     */
    fun bind(group: ChipGroup, selectedDay: Int, onChange: (Int) -> Unit) {
        group.check(chipIdFor(selectedDay))
        group.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != View.NO_ID) onChange(dayFor(checkedId))
        }
    }
}
