package com.raisethebar.engine

import java.time.LocalDate

/** Shared fixtures for the engine tests: the bundled program, library, rules and a fully working default gym. */
object Fixtures {
    val program: Program = SeedData.program
    val exercises: Map<String, Exercise> = SeedData.exercisesById
    val rules: InjuryRules = SeedData.injuryRules
    val gym: Map<String, Equipment> = SeedData.equipment.associateBy { it.id }

    /** Monday 19 October 2026, the plan start used across the tests. */
    val monday: LocalDate = LocalDate.of(2026, 10, 19)

    fun ctx(
        today: LocalDate = monday,
        gym: Map<String, Equipment> = this.gym,
        injuries: List<InjuryState> = emptyList(),
        skipToday: Set<String> = emptySet(),
    ) = GymContext(today, exercises, gym, injuries, rules, skipToday)

    fun input(
        date: LocalDate = monday,
        estimates: Map<String, Estimate> = emptyMap(),
        gym: Map<String, Equipment> = this.gym,
        injuries: List<InjuryState> = emptyList(),
        bodyKg: Double = 70.0,
        preInjury: Map<Long, Map<String, PreInjury>> = emptyMap(),
    ) = PlannerInput(date, monday, program, ctx(date, gym, injuries), estimates, emptyMap(), bodyKg, preInjury)

    fun Map<String, Equipment>.with(id: String, status: String, on: LocalDate = monday): Map<String, Equipment> =
        this + (id to getValue(id).copy(status = status, statusDate = on, brokenSince = if (status == EquipmentStatus.BROKEN) on else null))

    /** Monday to Saturday at 07:30, rest on Sunday. */
    val schedule: List<ScheduleDay> = Weekdays.defaults()
}
