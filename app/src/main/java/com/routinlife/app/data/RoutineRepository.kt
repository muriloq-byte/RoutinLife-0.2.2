package com.routinlife.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Ponto único de acesso à rotina, com LiveData para as telas se atualizarem sozinhas
 * depois de adicionar, editar, ativar/desativar ou excluir um item.
 * As telas de alarme (receiver/serviço) usam os métodos diretos, que não dependem de UI.
 */
object RoutineRepository {

    private lateinit var store: RoutineStore

    private val _items = MutableLiveData<List<RoutineItem>>(emptyList())
    val items: LiveData<List<RoutineItem>> = _items

    fun init(context: Context) {
        if (!::store.isInitialized) {
            store = RoutineStore(context)
        }
        reload()
    }

    fun reload() {
        _items.postValue(store.all())
    }

    fun all(): List<RoutineItem> = store.all()

    fun item(id: Long): RoutineItem? = store.findById(id)

    fun itemsAtSameTime(hour: Int, minute: Int, ignoreId: Long? = null): List<RoutineItem> =
        store.itemsAtSameTime(hour, minute, ignoreId)

    fun add(title: String, hour: Int, minute: Int): RoutineItem {
        val item = store.add(title, hour, minute)
        reload()
        return item
    }

    fun update(item: RoutineItem) {
        store.update(item)
        reload()
    }

    fun remove(id: Long) {
        store.remove(id)
        reload()
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        store.setEnabled(id, enabled)
        reload()
    }
}
