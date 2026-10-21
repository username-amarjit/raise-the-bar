package com.raisethebar.app.data

import com.raisethebar.app.db.EquipmentEntity
import com.raisethebar.app.db.ExerciseEntity
import com.raisethebar.app.db.ProgramDayEntity
import com.raisethebar.app.db.SlotEntity
import com.raisethebar.config.Rules
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.Exercise
import com.raisethebar.engine.ProgramDay
import com.raisethebar.engine.Slot

fun Exercise.toEntity() =
    ExerciseEntity(
        id = id,
        name = name,
        aliases = aliases,
        countsToward = countsToward,
        equipmentIds = equipmentIds,
        bigOrSmall = bigOrSmall,
        stresses = stresses,
        cues = cues,
        instructions = instructions,
        images = images,
        unilateral = unilateral,
        oneSidedVersionId = oneSidedVersionId,
        perSide = perSide,
        timed = timed,
        bodyweightBased = bodyweightBased,
        stepKg = stepKg,
        source = source,
        licence = licence,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        bodyRegion = bodyRegion,
        assisted = assisted,
        loadKind = loadKind,
        sourceId = sourceId,
    )

fun ExerciseEntity.toModel() =
    Exercise(
        id = id,
        name = name,
        aliases = aliases,
        countsToward = countsToward,
        equipmentIds = equipmentIds,
        bigOrSmall = bigOrSmall,
        stresses = stresses,
        cues = cues,
        instructions = instructions,
        images = images,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        unilateral = unilateral,
        oneSidedVersionId = oneSidedVersionId,
        perSide = perSide,
        timed = timed,
        bodyweightBased = bodyweightBased,
        assisted = assisted,
        loadKind = loadKind,
        bodyRegion = bodyRegion,
        stepKg = stepKg,
        source = source,
        sourceId = sourceId,
        licence = licence,
    )

fun Equipment.toEntity(position: Int) =
    EquipmentEntity(
        id = id,
        name = name,
        kind = kind,
        status = status,
        statusDate = statusDate,
        brokenSince = brokenSince,
        barKg = barKg,
        platePairsKg = platePairsKg,
        dumbbellPairsKg = dumbbellPairsKg,
        stackMin = stackMin,
        stackMax = stackMax,
        stackStep = stackStep,
        missingKg = missingKg,
        position = position,
        section = section,
        note = note,
    )

fun EquipmentEntity.toModel() =
    Equipment(
        id = id,
        name = name,
        kind = kind,
        section = section,
        note = note,
        status = status,
        statusDate = statusDate,
        brokenSince = brokenSince,
        barKg = barKg,
        platePairsKg = platePairsKg,
        dumbbellPairsKg = dumbbellPairsKg,
        stackMin = stackMin,
        stackMax = stackMax,
        stackStep = stackStep,
        missingKg = missingKg,
    )

fun ProgramDay.toEntity(position: Int) = ProgramDayEntity(id = id, name = name, focus = focus, position = position, kind = kind, dayType = dayType)

/** Home and away slots carry no rest time in program.json: big lifts rest 90 s and small ones 60 s. */
fun Slot.toEntity(dayId: String, firstChoiceIsBig: Boolean, promoted: String?) =
    SlotEntity(
        id = id,
        programDayId = dayId,
        position = position,
        sets = sets,
        repLow = repLow,
        repHigh = repHigh,
        secLow = secLow,
        secHigh = secHigh,
        restS = restS ?: if (firstChoiceIsBig) Rules.HOME_AWAY_REST_BIG_S else Rules.HOME_AWAY_REST_SMALL_S,
        options = options,
        stopLeft = stopLeft,
        note = note,
        promotedExerciseId = promoted,
    )
