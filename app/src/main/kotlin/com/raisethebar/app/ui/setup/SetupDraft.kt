package com.raisethebar.app.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.raisethebar.app.data.OldInjury
import com.raisethebar.app.data.Repo
import com.raisethebar.app.data.SetupInput
import com.raisethebar.app.data.dayOffCutoff
import com.raisethebar.app.data.formatKg
import com.raisethebar.engine.Diet
import com.raisethebar.engine.Equipment
import com.raisethebar.engine.Goal
import com.raisethebar.engine.MakeupSettings
import com.raisethebar.engine.Nutrition
import com.raisethebar.engine.ScheduleDay
import com.raisethebar.engine.SeedData
import com.raisethebar.engine.Validate
import com.raisethebar.engine.Weekdays
import java.time.LocalTime

/** Everything the five setup screens collect, as editable text and choices. Also used by Settings to edit a section. */
class SetupDraft : ViewModel() {
    var loaded by mutableStateOf(false)
    var weight by mutableStateOf("")
    var height by mutableStateOf("")
    var age by mutableStateOf("")
    var diet by mutableStateOf(Diet.VEG)
    var experience by mutableStateOf(EXPERIENCE.first().first)
    val injuries = mutableStateListOf<OldInjury>()
    var waist by mutableStateOf("")
    var goalChoice by mutableStateOf<String?>(null)
    var protein by mutableStateOf("")
    var proteinTouched by mutableStateOf(false)
    var kcal by mutableStateOf("")
    var kcalTouched by mutableStateOf(false)
    val schedule = mutableStateListOf<ScheduleDay>().apply { addAll(Weekdays.defaults()) }
    var notifications by mutableStateOf(true)
    val equipment = mutableStateListOf<Equipment>().apply { addAll(SeedData.equipment) }
    var cutoff by mutableStateOf(LocalTime.of(22, 0))
    var makeup by mutableStateOf(MakeupSettings())

    val weightKg: Double? get() = weight.toDoubleOrNull()?.takeIf(Validate::isBodyWeightKg)
    val heightCm: Double? get() = height.toDoubleOrNull()?.takeIf(Validate::isHeightCm)
    val ageYears: Int? get() = age.toIntOrNull()?.takeIf(Validate::isAge)
    val waistCm: Double? get() = waist.toDoubleOrNull()?.takeIf(Validate::isWaistCm)

    val suggestedGoal: String?
        get() {
            val w = waistCm ?: return null
            val h = heightCm ?: return null
            return Nutrition.suggestGoal(w, h)
        }

    val goal: String get() = goalChoice ?: suggestedGoal ?: Goal.LEAN_BULK

    val suggestedProtein: Int? get() = weightKg?.let(Nutrition::proteinTarget)
    val suggestedKcal: Int? get() = weightKg?.let { Nutrition.kcalGuess(it, goal) }

    val proteinG: Int? get() = if (proteinTouched) protein.toIntOrNull() else suggestedProtein
    val kcalTarget: Int? get() = if (kcalTouched) kcal.toIntOrNull() else suggestedKcal

    fun aboutValid() = weightKg != null && heightCm != null && ageYears != null

    fun goalValid() = waistCm != null && heightCm != null && (proteinG ?: 0) in PROTEIN_RANGE && (kcalTarget ?: 0) in KCAL_RANGE

    fun weekValid() = schedule.any { it.training }

    fun toInput() =
        SetupInput(
            weightKg = weightKg!!,
            heightCm = heightCm!!,
            waistCm = waistCm,
            age = ageYears!!,
            dietType = diet,
            experience = experience,
            goal = goal,
            proteinTargetG = proteinG!!,
            kcalTarget = kcalTarget!!,
            schedule = schedule.toList(),
            equipment = equipment.toList(),
            dayOffCutoff = cutoff,
            makeup = makeup,
            notificationsOn = notifications,
            oldInjuries = injuries.toList(),
        )

    /** Fills the draft from saved data, so Settings can edit one section. */
    suspend fun load(repo: Repo) {
        val p = repo.profile() ?: return
        weight = formatKg(p.weightKg)
        height = formatKg(p.heightCm)
        age = p.age.toString()
        diet = p.dietType
        experience = p.experience
        waist = p.waistCm?.let(::formatKg).orEmpty()
        goalChoice = p.goal
        protein = p.proteinTargetG.toString()
        proteinTouched = true
        kcal = p.kcalTarget.toString()
        kcalTouched = true
        schedule.clear()
        schedule.addAll(repo.schedule())
        notifications = p.notificationsOn
        equipment.clear()
        equipment.addAll(repo.gym().values)
        cutoff = repo.dayOffCutoff()
        makeup = repo.makeupSettings()
        loaded = true
    }

    companion object {
        val EXPERIENCE =
            listOf(
                "new" to "New to lifting",
                "under_1y" to "Under a year",
                "1_3y" to "1 to 3 years",
                "3y_plus" to "More than 3 years",
            )
        val DIETS = listOf(Diet.VEG to "Veg", Diet.EGG to "Egg", Diet.NON_VEG to "Non-veg")
        val PROTEIN_RANGE = 30..400
        val KCAL_RANGE = 1000..6000
    }
}
