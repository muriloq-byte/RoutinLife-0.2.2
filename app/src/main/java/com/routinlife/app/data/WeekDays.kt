package com.routinlife.app.data

import com.routinlife.app.R
import java.util.Calendar
import java.util.Locale

/**
 * Núcleo da organização semanal (0.3.0).
 *
 * Cada rotina pertence a UM dia da semana (constantes de java.util.Calendar:
 * DOMINGO=1 ... SABADO=7). As listas de dias diferentes nunca se misturam:
 * [forDay] é o único ponto de filtragem das telas e devolve apenas os itens daquele dia,
 * já na ordem das linhas do dia.
 *
 * As funções de data e de próximo disparo são puras (só java.util.Calendar), sem tocar em
 * classes do Android, para poderem ser testadas na JVM sem emulador.
 */
object WeekDays {

    /** Dia de itens antigos (0.2.x), que ainda não tinham dia atribuído. */
    const val LEGACY_DAY = 0

    /** Ordem de exibição da semana: segunda ... domingo. */
    val DISPLAY_ORDER = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )

    /** Dia da semana de hoje, no formato java.util.Calendar. */
    fun currentDay(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    /** Nome completo do dia ("Segunda-feira"), para mensagens. */
    fun nameRes(day: Int): Int = when (day) {
        Calendar.MONDAY -> R.string.day_monday
        Calendar.TUESDAY -> R.string.day_tuesday
        Calendar.WEDNESDAY -> R.string.day_wednesday
        Calendar.THURSDAY -> R.string.day_thursday
        Calendar.FRIDAY -> R.string.day_friday
        Calendar.SATURDAY -> R.string.day_saturday
        else -> R.string.day_sunday
    }

    /** Nome curto do dia ("Seg"), para chips e snackbars. */
    fun shortNameRes(day: Int): Int = when (day) {
        Calendar.MONDAY -> R.string.day_short_monday
        Calendar.TUESDAY -> R.string.day_short_tuesday
        Calendar.WEDNESDAY -> R.string.day_short_wednesday
        Calendar.THURSDAY -> R.string.day_short_thursday
        Calendar.FRIDAY -> R.string.day_short_friday
        Calendar.SATURDAY -> R.string.day_short_saturday
        else -> R.string.day_short_sunday
    }

    /**
     * Data (yyyy-MM-dd) da ocorrência mais recente de [day] que não é posterior a [from].
     * É a data que a tela exibe para aquele dia: marca feita nela vale só para ela e some
     * quando o mesmo dia da semana voltar na semana seguinte.
     */
    fun dateOf(day: Int, from: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val daysBack = (calendar.get(Calendar.DAY_OF_WEEK) - day + 7) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -daysBack)
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Data de hoje (yyyy-MM-dd). */
    fun today(): String = dateOf(currentDay())

    /**
     * Próxima ocorrência exata de (dia, hora, minuto) a partir de [from]:
     * o primeiro dia [day] cujo horário ainda não passou. Se o dia for inválido (só
     * possível em dado corrompido, nunca após a migração), cai no comportamento antigo do
     * app — a próxima ocorrência horária, qualquer dia — em vez de rodar para sempre.
     */
    fun nextTriggerMillis(day: Int, hour: Int, minute: Int, from: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val matchDay = day in 1..7
        while (
            (matchDay && calendar.get(Calendar.DAY_OF_WEEK) != day) ||
            calendar.timeInMillis <= from
        ) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /**
     * Os itens de UM dia, na ordem das linhas (posição manual dentro do dia).
     * Itens de outros dias são ignorados — é isso que garante que segunda e terça
     * nunca compartilham a mesma lista.
     */
    fun forDay(items: List<RoutineItem>, day: Int): List<RoutineItem> =
        items.filter { it.day == day }
            .sortedWith(compareBy({ it.order }, { it.hour * 60 + it.minute }, { it.id }))
}
