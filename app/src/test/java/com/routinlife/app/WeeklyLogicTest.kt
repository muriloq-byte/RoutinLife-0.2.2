package com.routinlife.app

import com.routinlife.app.data.RoutineItem
import com.routinlife.app.data.RoutineMigration
import com.routinlife.app.data.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Testes da lógica SEMANAL (0.3.0): próximo disparo respeitando o dia da semana,
 * isolamento das listas por dia, migração do formato diário antigo e conclusão presa à data.
 *
 * Referências: 2026-10-25 é domingo, 2026-10-26 é segunda e 2026-10-27 é terça.
 */
class WeeklyLogicTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    // ------------------------------------------------------------------ agendamento

    @Test
    fun `proximo disparo cai sempre no dia da semana do item`() {
        val monday10h = at(2026, 10, 26, 10)
        WeekDays.DISPLAY_ORDER.forEach { day ->
            val trigger = WeekDays.nextTriggerMillis(day, 9, 0, monday10h)
            val fired = Calendar.getInstance().apply { timeInMillis = trigger }
            assertEquals("dia de $day", day, fired.get(Calendar.DAY_OF_WEEK))
            assertEquals(9, fired.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, fired.get(Calendar.MINUTE))
            assertTrue("precisa estar no futuro", trigger > monday10h)
        }
    }

    @Test
    fun `hoje com horario ainda futuro dispara hoje`() {
        val monday8h = at(2026, 10, 26, 8)
        assertEquals(
            at(2026, 10, 26, 9),
            WeekDays.nextTriggerMillis(Calendar.MONDAY, 9, 0, monday8h)
        )
    }

    @Test
    fun `hoje com horario ja passado espera a proxima semana`() {
        val monday10h = at(2026, 10, 26, 10)
        assertEquals(
            at(2026, 11, 2, 9),
            WeekDays.nextTriggerMillis(Calendar.MONDAY, 9, 0, monday10h)
        )
    }

    @Test
    fun `cada dia da semana tem sua proxima ocorrência em semanas diferentes`() {
        val from = at(2026, 10, 27, 12)
        val triggers = WeekDays.DISPLAY_ORDER.map { day ->
            WeekDays.nextTriggerMillis(day, 8, 30, from)
        }
        // Sete dias distintos: segunda e terça não podem compartilhar o mesmo disparo.
        assertEquals(7, triggers.toSet().size)
    }

    // ------------------------------------------------------------------ isolamento por dia

    @Test
    fun `cada dia so enxerga os proprios itens`() {
        val segunda = RoutineItem(
            id = 1, hour = 9, minute = 0, title = "Estudos",
            day = Calendar.MONDAY, order = 0
        )
        val terca = RoutineItem(
            id = 2, hour = 9, minute = 0, title = "Estudos",
            day = Calendar.TUESDAY, order = 0
        )
        val all = listOf(terca, segunda)

        assertEquals(listOf(segunda), WeekDays.forDay(all, Calendar.MONDAY))
        assertEquals(listOf(terca), WeekDays.forDay(all, Calendar.TUESDAY))
        assertTrue(WeekDays.forDay(all, Calendar.MONDAY).none { it.day == Calendar.TUESDAY })
        assertTrue(WeekDays.forDay(all, Calendar.SUNDAY).isEmpty())
    }

    @Test
    fun `a lista do dia segue a posicao manual das linhas`() {
        val primeira = RoutineItem(
            id = 1, hour = 9, minute = 0, title = "Estudos",
            day = Calendar.MONDAY, order = 0
        )
        val segunda = RoutineItem(
            id = 2, hour = 21, minute = 0, title = "Dormir",
            day = Calendar.MONDAY, order = 1
        )
        assertEquals(
            listOf(primeira, segunda),
            WeekDays.forDay(listOf(segunda, primeira), Calendar.MONDAY)
        )
    }

    // ------------------------------------------------------------------ conclusão por data

    @Test
    fun `conclusao vale so para a data em que foi marcada`() {
        val item = RoutineItem(
            id = 1, hour = 9, minute = 0, title = "Estudos",
            day = Calendar.MONDAY, doneDate = "2026-10-26"
        )
        assertTrue(item.isDoneOn("2026-10-26"))
        assertFalse("terça não herda a conclusão de segunda", item.isDoneOn("2026-10-27"))
        assertFalse(
            "a segunda da semana seguinte começa desmarcada",
            item.isDoneOn("2026-11-02")
        )
    }

    @Test
    fun `dateOf devolve a data mais recente do dia exibido`() {
        val tuesday10h = at(2026, 10, 27, 10)
        assertEquals("2026-10-27", WeekDays.dateOf(Calendar.TUESDAY, tuesday10h))
        assertEquals("2026-10-26", WeekDays.dateOf(Calendar.MONDAY, tuesday10h))
        assertEquals("2026-10-25", WeekDays.dateOf(Calendar.SUNDAY, tuesday10h))
        assertEquals("2026-10-23", WeekDays.dateOf(Calendar.FRIDAY, tuesday10h))
    }

    // ------------------------------------------------------------------ migração 0.2.x -> 0.3.0

    @Test
    fun `item antigo vira sete itens um por dia`() {
        val legacy = RoutineItem(
            id = 1700000000000L, hour = 9, minute = 0, title = "Estudos",
            day = WeekDays.LEGACY_DAY, order = -1
        )
        val expanded = RoutineMigration.expandLegacy(listOf(legacy))

        assertEquals(7, expanded.size)
        assertEquals(WeekDays.DISPLAY_ORDER, expanded.map { it.day })
        assertEquals("ids únicos", 7, expanded.map { it.id }.toSet().size)
        assertTrue(expanded.all { it.order >= 0 })
        assertTrue(expanded.all { it.doneDate == null })
        assertTrue(expanded.all { it.title == "Estudos" })
        assertFalse(RoutineMigration.needsMigration(expanded))
    }

    @Test
    fun `migracao nao mexe em itens que ja sao semanais`() {
        val jaSemanal = RoutineItem(
            id = 5, hour = 9, minute = 0, title = "X",
            day = Calendar.MONDAY, order = 3
        )
        val items = listOf(jaSemanal)
        assertFalse(RoutineMigration.needsMigration(items))
        assertEquals(items, RoutineMigration.expandLegacy(items))
    }

    @Test
    fun `renumber usa a ordem cronologica dentro de cada dia`() {
        val tardinha = RoutineItem(
            id = 2, hour = 21, minute = 0, title = "Dormir",
            day = Calendar.MONDAY, order = -1
        )
        val manha = RoutineItem(
            id = 1, hour = 9, minute = 0, title = "Estudos",
            day = Calendar.MONDAY, order = -1
        )
        val renumbered = RoutineMigration.renumberChronologically(listOf(tardinha, manha))
        assertEquals(listOf(manha.id, tardinha.id), renumbered.map { it.id })
        assertEquals(listOf(0, 1), renumbered.map { it.order })
    }
}
