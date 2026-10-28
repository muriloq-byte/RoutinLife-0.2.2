package com.routinlife.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistência local da rotina SEMANAL.
 *
 * Decisão de arquitetura: a lista completa cabe tranquilamente em um JSON pequeno, então
 * usamos SharedPreferences com um JSONArray (org.json, que já vem no Android). Isso evita
 * banco de dados, migrations de esquema e bibliotecas de anotação, mantendo o app leve.
 * A lista é sempre gravada inteira (write-through) e a leitura é tolerante a arquivo
 * corrompido (nesse caso devolve lista vazia em vez de derrubar o app).
 *
 * Cada item carrega o SEU dia da semana (`day`) e a sua posição dentro dele (`order`).
 * Todas as consultas passam por WeekDays.forDay, então rotinas de dias diferentes nunca
 * aparecem juntas.
 *
 * Versão de esquema: 0.2.x gravava só {id, hour, minute, title, enabled}. A primeira
 * leitura detecta esses itens antigos, expande para os 7 dias (RoutineMigration) e regrava
 * o arquivo — a migração acontece uma única vez.
 */
class RoutineStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Todos os itens, agrupados por dia da semana e na ordem das linhas de cada dia. */
    fun all(): List<RoutineItem> = load()

    /** Somente as rotinas de UM dia, na ordem das linhas. */
    fun itemsForDay(day: Int): List<RoutineItem> = WeekDays.forDay(load(), day)

    fun findById(id: Long): RoutineItem? = load().firstOrNull { it.id == id }

    /** Itens do MESMO dia com o mesmo horário (dois itens no mesmo horário só colidem dentro do dia). */
    fun itemsAtSameTime(
        day: Int,
        hour: Int,
        minute: Int,
        ignoreId: Long? = null
    ): List<RoutineItem> =
        itemsForDay(day).filter { it.hour == hour && it.minute == minute && it.id != ignoreId }

    /**
     * Cria um item no dia [day]. A linha nasce na posição cronológica do horário dentro
     * daquele dia; a ordem manual dos outros dias não é tocada.
     */
    fun add(title: String, hour: Int, minute: Int, day: Int): RoutineItem {
        val items = load()
        val dayItems = WeekDays.forDay(items, day).toMutableList()
        val minuteOfDay = hour * 60 + minute
        val position = dayItems.indexOfFirst { it.hour * 60 + it.minute > minuteOfDay }
            .let { if (it < 0) dayItems.size else it }

        val item = RoutineItem(
            id = nextId(),
            hour = hour,
            minute = minute,
            title = title.trim(),
            day = day,
            order = position
        )
        dayItems.add(position, item)
        val renumbered = dayItems.mapIndexed { index, existing -> existing.copy(order = index) }
        val stored = renumbered.first { it.id == item.id }

        save(items.filter { it.day != day } + renumbered)
        return stored
    }

    /** Atualiza título/horário do item, preservando dia, posição e conclusão. */
    fun update(item: RoutineItem) {
        save(load().map { if (it.id == item.id) item else it })
    }

    fun remove(id: Long) {
        save(load().filterNot { it.id == id })
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        save(load().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /** Marca (ou desmarca, com null) a conclusão presa a uma data. */
    fun setDone(id: Long, doneDate: String?) {
        save(load().map { if (it.id == id) it.copy(doneDate = doneDate) else it })
    }

    /** Grava a nova ordem das linhas de UM dia. Itens de outros dias ficam intactos. */
    fun reorder(day: Int, orderedIds: List<Long>) {
        val positions = orderedIds.withIndex().associate { (index, id) -> id to index }
        save(
            load().map { item ->
                val position = if (item.day == day) positions[item.id] else null
                if (position != null) item.copy(order = position) else item
            }
        )
    }

    private fun nextId(): Long = System.currentTimeMillis()

    private fun load(): MutableList<RoutineItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val parsed = mutableListOf<RoutineItem>()
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                parsed += RoutineItem(
                    id = obj.optLong("id", index.toLong()),
                    hour = obj.optInt("hour", 0),
                    minute = obj.optInt("minute", 0),
                    title = obj.optString("title", ""),
                    enabled = obj.optBoolean("enabled", true),
                    day = obj.optInt("day", WeekDays.LEGACY_DAY),
                    order = obj.optInt("order", -1),
                    doneDate = obj.optString("doneDate", "").takeIf { it.isNotEmpty() }
                )
            }
            val normalized = normalize(parsed)
            if (normalized != parsed) save(normalized)
            normalized.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    /**
     * Primeira leitura após a atualização: expande os itens antigos para os 7 dias.
     * Em seguida, garante que todo item tenha posição válida dentro do seu dia.
     */
    private fun normalize(items: List<RoutineItem>): List<RoutineItem> = when {
        RoutineMigration.needsMigration(items) -> RoutineMigration.expandLegacy(items)
        RoutineMigration.needsOrderFix(items) -> RoutineMigration.renumberChronologically(items)
        else -> items
    }

    private fun save(items: List<RoutineItem>) {
        val array = JSONArray()
        items.sortedWith(
            compareBy({ WeekDays.DISPLAY_ORDER.indexOf(it.day) }, { it.order }, { it.id })
        ).forEach { item ->
            val obj = JSONObject()
                .put("id", item.id)
                .put("hour", item.hour)
                .put("minute", item.minute)
                .put("title", item.title)
                .put("enabled", item.enabled)
                .put("day", item.day)
                .put("order", item.order)
            if (item.doneDate != null) obj.put("doneDate", item.doneDate)
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "routinlife_prefs"
        const val KEY_ITEMS = "routine_items"
    }
}
