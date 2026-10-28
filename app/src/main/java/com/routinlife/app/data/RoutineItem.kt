package com.routinlife.app.data

/**
 * Uma rotina dentro de um dia da semana.
 *
 * - [day]: dia ao qual a rotina pertence (constantes de java.util.Calendar:
 *   DOMINGO=1 ... SABADO=7). Itens de dias diferentes são registros totalmente
 *   independentes: editar, excluir ou concluir um não afeta os outros dias.
 * - [order]: posição da linha dentro do dia (0 = primeira). Nasce cronológica na criação e
 *   o usuário pode reordenar arrastando a linha na aba "Minha Rotina".
 * - [doneDate]: data (yyyy-MM-dd) em que foi marcada como concluída. Guardar a data e não
 *   um booleano mantém a conclusão presa ao dia certo: a conclusão de segunda não aparece
 *   em terça nem reaparece na segunda da semana seguinte.
 *
 * O texto do horário é exibido no formato 24h (hh:mm).
 */
data class RoutineItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val title: String,
    val enabled: Boolean = true,
    val day: Int,
    val order: Int = 0,
    val doneDate: String? = null
) {
    val timeLabel: String
        get() = String.format("%02d:%02d", hour, minute)

    /** Considera concluída quando exibida na data [date] (dia específico na tela). */
    fun isDoneOn(date: String): Boolean = doneDate != null && doneDate == date
}
