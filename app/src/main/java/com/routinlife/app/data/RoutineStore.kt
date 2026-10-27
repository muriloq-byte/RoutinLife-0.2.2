package com.routinlife.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistência local da rotina.
 *
 * Decisão de arquitetura: a lista completa cabe tranquilamente em um JSON pequeno, então
 * usamos SharedPreferences com um JSONArray (org.json, que já vem no Android). Isso evita
 * banco de dados, migrations e bibliotecas de anotação, mantendo o app leve e sem risco de
 * perder dados entre versões. A lista é sempre gravada inteira (write-through) e a leitura
 * é tolerante a arquivo corrompido (nesse caso devolve lista vazia em vez de derrubar o app).
 */
class RoutineStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Todos os itens em ordem cronológica. */
    fun all(): List<RoutineItem> =
        load().sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))

    fun findById(id: Long): RoutineItem? = load().firstOrNull { it.id == id }

    fun itemsAtSameTime(hour: Int, minute: Int, ignoreId: Long? = null): List<RoutineItem> =
        all().filter { it.hour == hour && it.minute == minute && it.id != ignoreId }

    fun add(title: String, hour: Int, minute: Int): RoutineItem {
        val item = RoutineItem(
            id = nextId(),
            hour = hour,
            minute = minute,
            title = title.trim()
        )
        save(load() + item)
        return item
    }

    fun update(item: RoutineItem) {
        save(load().map { if (it.id == item.id) item else it })
    }

    fun remove(id: Long) {
        save(load().filterNot { it.id == id })
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        save(load().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun nextId(): Long = System.currentTimeMillis()

    private fun load(): MutableList<RoutineItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<RoutineItem>()
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                list += RoutineItem(
                    id = obj.optLong("id", index.toLong()),
                    hour = obj.optInt("hour", 0),
                    minute = obj.optInt("minute", 0),
                    title = obj.optString("title", ""),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun save(items: List<RoutineItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("title", item.title)
                    .put("enabled", item.enabled)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "routinlife_prefs"
        const val KEY_ITEMS = "routine_items"
    }
}
