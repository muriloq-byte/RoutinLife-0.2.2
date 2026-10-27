package com.routinlife.app.data

/**
 * Um item da rotina diária: um horário, o nome da atividade e se o alarme está ativo.
 * O texto do horário é exibido no formato 24h (hh:mm).
 */
data class RoutineItem(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val title: String,
    val enabled: Boolean = true
) {
    val timeLabel: String
        get() = String.format("%02d:%02d", hour, minute)
}
