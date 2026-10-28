package com.routinlife.app.data

/**
 * Migração dos dados da versão diária (0.2.x) para a semanal (0.3.0).
 *
 * No app antigo existia UMA rotina única que se repetia em todos os dias. A migração mais
 * coerente com esse código é copiar cada item antigo para os 7 dias da semana: o usuário
 * continua com os mesmos alarmes em todos os dias (comportamento idêntico ao de antes) e,
 * a partir daí, edita cada dia de forma independente.
 *
 * Detalhes:
 * - O id de cada cópia é derivado do original (`original * 10 + índice`), garantindo ids
 *   únicos sem depender de relógio (o app antigo usava System.currentTimeMillis(), que
 *   empilharia as 7 cópias no mesmo milissegundo).
 * - A ordem das linhas nasce cronológica em cada dia.
 * - Estado de conclusão antigo não existia, então as cópias nascem não concluídas.
 *
 * A migração roda UMA vez: [expandLegacy] é chamado na leitura, o resultado é gravado na
 * hora e as próximas leituras já encontram os campos `day`/`order` e não migram de novo.
 *
 * Funções puras (sem Android), cobertas por app/src/test/.../WeeklyLogicTest.kt.
 */
object RoutineMigration {

    /** Ainda existe item sem dia atribuído (arquivo gravado pelo 0.2.x)? */
    fun needsMigration(items: List<RoutineItem>): Boolean =
        items.any { it.day == WeekDays.LEGACY_DAY }

    /** Existe item sem posição válida dentro do dia? */
    fun needsOrderFix(items: List<RoutineItem>): Boolean =
        items.any { it.order < 0 }

    /** Copia cada item antigo para os 7 dias da semana. Idempotente. */
    fun expandLegacy(items: List<RoutineItem>): List<RoutineItem> {
        if (!needsMigration(items)) return items

        val expanded = mutableListOf<RoutineItem>()
        items.forEach { item ->
            if (item.day == WeekDays.LEGACY_DAY) {
                WeekDays.DISPLAY_ORDER.forEachIndexed { index, day ->
                    expanded += item.copy(
                        id = item.id * 10 + index,
                        day = day,
                        order = 0,
                        doneDate = null
                    )
                }
            } else {
                expanded += item
            }
        }
        return renumberChronologically(expanded)
    }

    /**
     * Atribui a posição de cada linha dentro do seu dia, em ordem cronológica.
     * Usado pela migração e como correção defensiva de ordem inválida.
     */
    fun renumberChronologically(items: List<RoutineItem>): List<RoutineItem> {
        val result = mutableListOf<RoutineItem>()
        WeekDays.DISPLAY_ORDER.forEach { day ->
            items.filter { it.day == day }
                .sortedWith(compareBy({ it.hour * 60 + it.minute }, { it.id }))
                .forEachIndexed { index, item -> result += item.copy(order = index) }
        }
        // Itens fora dos 7 dias não devem existir; se existirem, são preservados sem tocar.
        result += items.filter { it.day !in WeekDays.DISPLAY_ORDER }
        return result
    }
}
