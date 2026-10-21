package com.raisethebar.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Milestone 2: every slot option in section 7 resolves to an exercise record. */
class ExerciseLibraryTest {
    private val lib = SeedData.exercisesById
    private val program = SeedData.program
    private val gym = SeedData.equipment.associateBy { it.id }

    @Test
    fun `every slot option resolves to an exercise record`() {
        val missing = program.days.flatMap { d -> d.slots.flatMap { s -> s.options.filter { it !in lib }.map { "${s.id}: $it" } } }
        assertTrue(missing.isEmpty(), "unresolved slot options: $missing")
    }

    @Test
    fun `every make-up candidate and one-sided version resolves`() {
        val missing = program.makeupCandidates.values.flatten().filter { it !in lib }
        assertTrue(missing.isEmpty(), "unresolved make-up candidates: $missing")
        val oneSided = lib.values.mapNotNull { it.oneSidedVersionId }.filter { it !in lib }
        assertTrue(oneSided.isEmpty(), "unresolved one-sided versions: $oneSided")
    }

    @Test
    fun `the program has the six rotation days plus home, away and pre-trip templates`() {
        assertEquals(listOf("push_a", "pull_a", "legs_a", "push_b", "pull_b", "legs_b"), program.rotation)
        listOf("push", "pull", "legs").forEach { assertNotNull(program.homeDay(it), it) }
        assertNotNull(program.dayOrNull(program.awayTemplates.getValue("bodyweight")))
        assertNotNull(program.dayOrNull(program.awayTemplates.getValue("dumbbells")))
        assertNotNull(program.dayOrNull(program.preTrip))
    }

    @Test
    fun `about 100 exercises, each with three form cues and known equipment`() {
        assertTrue(lib.size in 90..130, "library has ${lib.size} exercises")
        lib.values.forEach { ex ->
            assertEquals(3, ex.cues.size, ex.id)
            assertTrue(ex.equipmentIds.all { it in gym }, "${ex.id} uses unknown equipment ${ex.equipmentIds}")
            assertTrue(ex.countsToward.all { it in Groups.ALL }, ex.id)
        }
    }

    @Test
    fun `big and small follow the section 5 lists`() {
        val big = listOf("barbell_bench_press", "lat_pulldown", "barbell_row", "dips", "barbell_back_squat", "leg_press", "barbell_hip_thrust")
        val small =
            listOf(
                "dumbbell_lateral_raise",
                "ez_bar_curl",
                "triceps_rope_pushdown",
                "cable_fly",
                "face_pull",
                "leg_extension",
                "lying_leg_curl",
                "leg_press_calf_raise",
                "cable_crunch",
                "pallof_press",
            )
        big.forEach { assertTrue(lib.getValue(it).isBig, "$it should be big") }
        small.forEach { assertTrue(!lib.getValue(it).isBig, "$it should be small") }
    }

    @Test
    fun `weekly targets match the full program`() {
        val counted = mutableMapOf<String, Int>()
        program.rotation.map(program::day).forEach { day ->
            day.slots.forEach { slot -> lib.getValue(slot.options.first()).countsToward.forEach { g -> counted.merge(g, slot.sets, Int::plus) } }
        }
        assertEquals(program.weeklyTargets, counted.toSortedMap().filterKeys { it in program.weeklyTargets })
    }

    @Test
    fun `barbell steps are 2_5 kg for upper body and 5 kg for lower body`() {
        assertEquals(2.5, lib.getValue("barbell_bench_press").stepKg)
        assertEquals(5.0, lib.getValue("barbell_back_squat").stepKg)
        assertEquals(5.0, lib.getValue("barbell_romanian_deadlift").stepKg)
        assertEquals(LoadKind.DUMBBELL, lib.getValue("dumbbell_lateral_raise").loadKind)
        assertEquals(LoadKind.STACK, lib.getValue("leg_press").loadKind)
        assertEquals(LoadKind.BODYWEIGHT, lib.getValue("pull_ups").loadKind)
        assertEquals(LoadKind.ASSISTED, lib.getValue("assisted_pull_up").loadKind)
        assertEquals(LoadKind.NONE, lib.getValue("plank").loadKind)
    }
}
