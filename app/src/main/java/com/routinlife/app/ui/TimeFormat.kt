package com.routinlife.app.ui

import java.util.Locale

/** Formata o horário no padrão 24h usado em toda a interface (ex.: 09:00). */
fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
