package com.myvitory.app.data

import com.myvitory.app.data.db.ExerciseEntity
import com.myvitory.app.domain.Muscle.ABS
import com.myvitory.app.domain.Muscle.BACK
import com.myvitory.app.domain.Muscle.BICEPS
import com.myvitory.app.domain.Muscle.CALVES
import com.myvitory.app.domain.Muscle.CHEST
import com.myvitory.app.domain.Muscle.FOREARMS
import com.myvitory.app.domain.Muscle.GLUTES
import com.myvitory.app.domain.Muscle.HAMSTRINGS
import com.myvitory.app.domain.Muscle.QUADS
import com.myvitory.app.domain.Muscle.SHOULDERS
import com.myvitory.app.domain.Muscle.TRICEPS
import java.time.DayOfWeek

/** Catálogo inicial e plano de exemplo (divisão ABCDE), inseridos na primeira abertura. */
object SeedData {

    val exercises = listOf(
        ExerciseEntity("supino_reto", "Supino reto com barra", "Barra", listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
        ExerciseEntity("supino_inclinado_halteres", "Supino inclinado com halteres", "Halteres", listOf(CHEST), listOf(SHOULDERS, TRICEPS)),
        ExerciseEntity("crucifixo_maquina", "Crucifixo na máquina", "Máquina", listOf(CHEST), emptyList()),
        ExerciseEntity("crossover", "Crossover", "Polia", listOf(CHEST), listOf(SHOULDERS)),
        ExerciseEntity("flexao", "Flexão de braço", "Peso corporal", listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
        ExerciseEntity("triceps_polia", "Tríceps na polia", "Polia", listOf(TRICEPS), emptyList()),
        ExerciseEntity("triceps_frances", "Tríceps francês", "Halter", listOf(TRICEPS), emptyList()),
        ExerciseEntity("mergulho_paralelas", "Mergulho nas paralelas", "Peso corporal", listOf(TRICEPS), listOf(CHEST, SHOULDERS)),
        ExerciseEntity("puxada_frente", "Puxada frente", "Polia", listOf(BACK), listOf(BICEPS)),
        ExerciseEntity("remada_curvada", "Remada curvada com barra", "Barra", listOf(BACK), listOf(BICEPS)),
        ExerciseEntity("remada_baixa", "Remada baixa no cabo", "Polia", listOf(BACK), listOf(BICEPS)),
        ExerciseEntity("remada_unilateral", "Remada unilateral", "Halter", listOf(BACK), listOf(BICEPS)),
        ExerciseEntity("barra_fixa", "Barra fixa", "Peso corporal", listOf(BACK), listOf(BICEPS)),
        ExerciseEntity("rosca_direta", "Rosca direta", "Barra", listOf(BICEPS), listOf(FOREARMS)),
        ExerciseEntity("rosca_alternada", "Rosca alternada", "Halteres", listOf(BICEPS), listOf(FOREARMS)),
        ExerciseEntity("rosca_martelo", "Rosca martelo", "Halteres", listOf(BICEPS), listOf(FOREARMS)),
        ExerciseEntity("agachamento_livre", "Agachamento livre", "Barra", listOf(QUADS), listOf(GLUTES, HAMSTRINGS)),
        ExerciseEntity("leg_press_45", "Leg press 45°", "Máquina", listOf(QUADS), listOf(GLUTES)),
        ExerciseEntity("agachamento_hack", "Agachamento hack", "Máquina", listOf(QUADS), listOf(GLUTES)),
        ExerciseEntity("agachamento_bulgaro", "Agachamento búlgaro", "Halteres", listOf(QUADS), listOf(GLUTES)),
        ExerciseEntity("cadeira_extensora", "Cadeira extensora", "Máquina", listOf(QUADS), emptyList()),
        ExerciseEntity("stiff", "Stiff com barra", "Barra", listOf(HAMSTRINGS), listOf(GLUTES)),
        ExerciseEntity("mesa_flexora", "Mesa flexora", "Máquina", listOf(HAMSTRINGS), emptyList()),
        ExerciseEntity("levantamento_terra", "Levantamento terra", "Barra", listOf(HAMSTRINGS), listOf(GLUTES, BACK)),
        ExerciseEntity("elevacao_pelvica", "Elevação pélvica", "Barra", listOf(GLUTES), listOf(HAMSTRINGS)),
        ExerciseEntity("cadeira_abdutora", "Cadeira abdutora", "Máquina", listOf(GLUTES), emptyList()),
        ExerciseEntity("panturrilha_em_pe", "Panturrilha em pé", "Máquina", listOf(CALVES), emptyList()),
        ExerciseEntity("panturrilha_sentado", "Panturrilha sentado", "Máquina", listOf(CALVES), emptyList()),
        ExerciseEntity("panturrilha_leg_press", "Panturrilha no leg press", "Máquina", listOf(CALVES), emptyList()),
        ExerciseEntity("desenvolvimento_halteres", "Desenvolvimento com halteres", "Halteres", listOf(SHOULDERS), listOf(TRICEPS)),
        ExerciseEntity("elevacao_lateral", "Elevação lateral", "Halteres", listOf(SHOULDERS), emptyList()),
        ExerciseEntity("crucifixo_invertido", "Crucifixo invertido", "Máquina", listOf(SHOULDERS), listOf(BACK)),
        ExerciseEntity("abdominal_polia", "Abdominal na polia", "Polia", listOf(ABS), emptyList()),
        ExerciseEntity("elevacao_pernas", "Elevação de pernas", "Peso corporal", listOf(ABS), emptyList()),
    )

    data class Item(val exerciseId: String, val sets: Int, val reps: Int, val restSec: Int)
    data class Template(val code: String, val name: String, val day: DayOfWeek, val items: List<Item>)

    val templates = listOf(
        Template(
            "A", "Peito + Tríceps", DayOfWeek.MONDAY,
            listOf(
                Item("supino_reto", 4, 8, 120),
                Item("supino_inclinado_halteres", 3, 10, 90),
                Item("crossover", 3, 12, 60),
                Item("triceps_polia", 3, 12, 60),
                Item("triceps_frances", 3, 10, 60),
            ),
        ),
        Template(
            "B", "Costas + Bíceps", DayOfWeek.TUESDAY,
            listOf(
                Item("puxada_frente", 4, 10, 90),
                Item("remada_curvada", 4, 8, 120),
                Item("remada_baixa", 3, 10, 90),
                Item("rosca_direta", 3, 10, 60),
                Item("rosca_martelo", 3, 12, 60),
            ),
        ),
        Template(
            "C", "Pernas", DayOfWeek.WEDNESDAY,
            listOf(
                Item("agachamento_livre", 4, 8, 150),
                Item("leg_press_45", 3, 10, 120),
                Item("cadeira_extensora", 3, 12, 60),
                Item("mesa_flexora", 3, 12, 60),
                Item("panturrilha_em_pe", 4, 15, 45),
            ),
        ),
        Template(
            "D", "Ombros + Abdômen", DayOfWeek.THURSDAY,
            listOf(
                Item("desenvolvimento_halteres", 4, 10, 90),
                Item("elevacao_lateral", 4, 12, 60),
                Item("crucifixo_invertido", 3, 12, 60),
                Item("abdominal_polia", 3, 15, 45),
                Item("elevacao_pernas", 3, 12, 45),
            ),
        ),
        Template(
            "E", "Posterior + Glúteos", DayOfWeek.FRIDAY,
            listOf(
                Item("stiff", 4, 10, 120),
                Item("elevacao_pelvica", 4, 10, 90),
                Item("mesa_flexora", 3, 12, 60),
                Item("agachamento_bulgaro", 3, 10, 90),
                Item("cadeira_abdutora", 3, 15, 60),
            ),
        ),
    )
}
