package com.routinlife.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Ponto único de acesso à rotina SEMANAL, com LiveData para as telas se atualizarem
 * sozinhas depois de adicionar, editar, reordenar, concluir, ativar/desativar ou excluir.
 * As telas de alarme (receiver/serviço) usam os métodos diretos, que não dependem de UI.
 *
 * A LiveData continua emitindo TODOS os itens; cada tela filtra pelo dia que está exibindo
 * (WeekDays.forDay), de modo que os dados de um dia nunca vazam para outro.
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

    /** Rotinas de UM dia, na ordem das linhas. */
    fun itemsForDay(day: Int): List<RoutineItem> = store.itemsForDay(day)

    fun item(id: Long): RoutineItem? = store.findById(id)

    /** Itens do MESMO dia no mesmo horário (colisão de alarme só existe dentro do dia). */
    fun itemsAtSameTime(
        day: Int,
        hour: Int,
        minute: Int,
        ignoreId: Long? = null
    ): List<RoutineItem> = store.itemsAtSameTime(day, hour, minute, ignoreId)

    fun add(title: String, hour: Int, minute: Int, day: Int): RoutineItem {
        val item = store.add(title, hour, minute, day)
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

    /** Marca a conclusão presa a uma data (null = desmarcar). */
    fun setDone(id: Long, doneDate: String?) {
        store.setDone(id, doneDate)
        reload()
    }

    /** Grava a ordem das linhas de um dia depois de o usuário arrastar. */
    fun reorder(day: Int, orderedIds: List<Long>) {
        store.reorder(day, orderedIds)
        reload()
    }
}
