package com.hananel.workout.data

import com.hananel.workout.domain.*
import java.util.UUID

// Content adapted from the original Workout Tracker catalogue. No Android resource IDs are persisted.
object Catalog {
    val exercises = listOf(
        Exercise("bank_chest_dumbbell_press", "bank_chest_dumbbell_press", "לחיצת חזה דמבלס", "Dumbbell Chest Press", Muscle.CHEST, "משקולות יד", "Dumbbells", 5, 10, Measure.REPS, 60, true, targetMax = 12),
        Exercise("bank_chest_barbell_press", "bank_chest_barbell_press", "לחיצת חזה ברבל", "Barbell Bench Press", Muscle.CHEST, "מוט", "Barbell", 5, 8, Measure.REPS, 90, true, targetMax = 10),
        Exercise("bank_chest_cable_press", "bank_chest_cable_press", "לחיצת חזה בכבל", "Cable Chest Press", Muscle.CHEST, "כבל", "Cable", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_chest_flyes", "bank_chest_flyes", "פרפר במכונה", "Machine Flyes", Muscle.CHEST, "מכונה", "Machine", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_chest_dumbbell_flyes", "bank_chest_dumbbell_flyes", "פרפר דמבלס", "Dumbbell Flyes", Muscle.CHEST, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_chest_pushups", "bank_chest_pushups", "שכיבות סמיכה", "Push-ups", Muscle.CHEST, "משקל גוף", "Bodyweight", 4, 15, Measure.REPS, 45, false, targetMax = 20),
        Exercise("bank_back_lat_pulldown", "bank_back_lat_pulldown", "משיכה בפולי עליון רחב", "Wide Lat Pulldown", Muscle.BACK, "כבל", "Cable", 5, 10, Measure.REPS, 60, true, targetMax = 12),
        Exercise("bank_back_cable_row", "bank_back_cable_row", "חתירה בפולי נמוך", "Seated Cable Row", Muscle.BACK, "כבל", "Cable", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_back_cable_pullover", "bank_back_cable_pullover", "פולאובר בכבל", "Cable Pullover", Muscle.BACK, "כבל", "Cable", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_back_deadlift", "bank_back_deadlift", "דדליפט", "Deadlift", Muscle.BACK, "מוט", "Barbell", 5, 6, Measure.REPS, 120, true, targetMax = 8),
        Exercise("bank_back_pullups", "bank_back_pullups", "מתקני סטים", "Pull-ups", Muscle.BACK, "משקל גוף", "Bodyweight", 4, 8, Measure.REPS, 60, false, targetMax = 12),
        Exercise("bank_back_dumbbell_row", "bank_back_dumbbell_row", "חתירה דמבלס", "Dumbbell Row", Muscle.BACK, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_shoulders_dumbbell_press", "bank_shoulders_dumbbell_press", "לחיצת כתפיים דמבלס", "Dumbbell Shoulder Press", Muscle.SHOULDERS, "משקולות יד", "Dumbbells", 4, 10, Measure.REPS, 60, true, targetMax = 12),
        Exercise("bank_shoulders_barbell_press", "bank_shoulders_barbell_press", "לחיצת כתפיים ברבל", "Barbell Shoulder Press", Muscle.SHOULDERS, "מוט", "Barbell", 4, 8, Measure.REPS, 60, true, targetMax = 10),
        Exercise("bank_shoulders_lateral_raise", "bank_shoulders_lateral_raise", "הרחקת כתפיים צידית", "Lateral Raises", Muscle.SHOULDERS, "משקולות יד", "Dumbbells", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_shoulders_front_raise", "bank_shoulders_front_raise", "הרחקת כתפיים קדמית", "Front Raises", Muscle.SHOULDERS, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_shoulders_rear_delt_flyes", "bank_shoulders_rear_delt_flyes", "פרפר אחורי", "Rear Delt Flyes", Muscle.SHOULDERS, "משקולות יד", "Dumbbells", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_arms_dumbbell_curl", "bank_arms_dumbbell_curl", "כפיפת מרפקים עם דמבלס", "Dumbbell Curls", Muscle.ARMS, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_arms_barbell_curl", "bank_arms_barbell_curl", "כפיפת מרפקים עם ברבל", "Barbell Curls", Muscle.ARMS, "מוט", "Barbell", 4, 10, Measure.REPS, 45, true, targetMax = 12),
        Exercise("bank_arms_hammer_curl", "bank_arms_hammer_curl", "פטיש", "Hammer Curls", Muscle.ARMS, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_arms_cable_tricep_extension", "bank_arms_cable_tricep_extension", "יישור מרפקים בכבל", "Cable Tricep Extension", Muscle.ARMS, "כבל", "Cable", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_arms_dumbbell_tricep_extension", "bank_arms_dumbbell_tricep_extension", "יישור מרפקים עם דמבלס", "Dumbbell Tricep Extension", Muscle.ARMS, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 15),
        Exercise("bank_arms_dips", "bank_arms_dips", "שכיבות טריצפס", "Dips", Muscle.ARMS, "משקל גוף", "Bodyweight", 4, 10, Measure.REPS, 60, false, targetMax = 15),
        Exercise("bank_legs_squats", "bank_legs_squats", "סקוואט", "Squats", Muscle.LEGS, "מוט", "Barbell", 5, 10, Measure.REPS, 90, true, targetMax = 12),
        Exercise("bank_legs_front_squats", "bank_legs_front_squats", "סקוואט קדמי", "Front Squats", Muscle.LEGS, "מוט", "Barbell", 4, 10, Measure.REPS, 90, true, targetMax = 12),
        Exercise("bank_legs_leg_press", "bank_legs_leg_press", "כבישת רגליים", "Leg Press", Muscle.LEGS, "מכונה", "Machine", 4, 12, Measure.REPS, 60, true, targetMax = 15),
        Exercise("bank_legs_leg_curl", "bank_legs_leg_curl", "כפיפת רגליים", "Leg Curls", Muscle.LEGS, "מכונה", "Machine", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_legs_leg_extension", "bank_legs_leg_extension", "יישור רגליים", "Leg Extension", Muscle.LEGS, "מכונה", "Machine", 4, 15, Measure.REPS, 45, true, targetMax = 20),
        Exercise("bank_legs_lunges", "bank_legs_lunges", "לאנג'ס", "Walking Lunges", Muscle.LEGS, "משקולות יד", "Dumbbells", 4, 12, Measure.REPS, 45, true, targetMax = 12),
        Exercise("bank_legs_cossack_squat", "bank_legs_cossack_squat", "קוזאק סקוואט", "Cossack Squats", Muscle.LEGS, "משקל גוף", "Bodyweight", 4, 10, Measure.REPS, 45, false, targetMax = 10),
        Exercise("bank_legs_step_ups", "bank_legs_step_ups", "עליות על במה", "Step Ups", Muscle.LEGS, "ספסל", "Box/Bench", 4, 15, Measure.REPS, 45, false, targetMax = 15),
        Exercise("bank_legs_calf_raise", "bank_legs_calf_raise", "הרמת עקבים", "Calf Raises", Muscle.LEGS, "מכונה", "Machine", 4, 20, Measure.REPS, 30, true, targetMax = 25),
        Exercise("bank_core_plank", "bank_core_plank", "פלאנק", "Plank", Muscle.CORE, "משקל גוף", "Bodyweight", 4, 60, Measure.SECONDS, 30, false, targetMax = 60),
        Exercise("bank_core_crunches", "bank_core_crunches", "כפיפות בטן", "Crunches", Muscle.CORE, "משקל גוף", "Bodyweight", 4, 20, Measure.REPS, 30, false, targetMax = 25),
        Exercise("bank_core_russian_twist", "bank_core_russian_twist", "טוויסט רוסי", "Russian Twist", Muscle.CORE, "משקל גוף", "Bodyweight", 4, 20, Measure.REPS, 30, false, targetMax = 20),
        Exercise("bank_core_leg_raises", "bank_core_leg_raises", "הרמת רגליים", "Leg Raises", Muscle.CORE, "משקל גוף", "Bodyweight", 4, 15, Measure.REPS, 30, false, targetMax = 20),
        Exercise("bank_cardio_hiit", "bank_cardio_hiit", "HIIT", "HIIT Training", Muscle.CARDIO, "הליכון / אופניים", "Treadmill/Bike", 1, 30, Measure.MINUTES, 0, false, targetMax = 30),
        Exercise("bank_cardio_running", "bank_cardio_running", "ריצה", "Running", Muscle.CARDIO, "הליכון", "Treadmill", 1, 30, Measure.MINUTES, 0, false, targetMax = 40),
        Exercise("bank_cardio_cycling", "bank_cardio_cycling", "אופניים", "Cycling", Muscle.CARDIO, "אופניים", "Bike", 1, 30, Measure.MINUTES, 0, false, targetMax = 45),
        Exercise("bank_cardio_rowing", "bank_cardio_rowing", "חתירה", "Rowing", Muscle.CARDIO, "מכשיר חתירה", "Rowing Machine", 1, 20, Measure.MINUTES, 0, false, targetMax = 30),
        Exercise("bank_cardio_elliptical", "bank_cardio_elliptical", "אליפטי", "Elliptical", Muscle.CARDIO, "אליפטיקל", "Elliptical", 1, 30, Measure.MINUTES, 0, false, targetMax = 40),
    )
    fun fresh(exercise: Exercise) = exercise.copy(id = UUID.randomUUID().toString())
    fun starter(abc: Boolean = false, english: Boolean = false): List<Routine> {
        val groups = if (abc) listOf(
            Triple("חזה ויד אחורית", "Chest & triceps", listOf(Muscle.CHEST, Muscle.ARMS)),
            Triple("גב ויד קדמית", "Back & biceps", listOf(Muscle.BACK, Muscle.ARMS)),
            Triple("רגליים וכתפיים", "Legs & shoulders", listOf(Muscle.LEGS, Muscle.SHOULDERS))
        ) else listOf(
            Triple("חזה וכתפיים", "Chest & shoulders", listOf(Muscle.CHEST, Muscle.SHOULDERS)),
            Triple("גב", "Back", listOf(Muscle.BACK)),
            Triple("רגליים", "Legs", listOf(Muscle.LEGS)),
            Triple("ידיים ובטן", "Arms & core", listOf(Muscle.ARMS, Muscle.CORE)),
            Triple("אירובי", "Cardio", listOf(Muscle.CARDIO))
        )
        val scheduled = listOf(0, 2, 4, 6, 1)
        return groups.mapIndexed { index, (he, en, muscles) ->
            val selected = muscles.flatMap { m ->
                val candidates = exercises.filter { it.muscle == m }
                if (abc && m == Muscle.ARMS && index == 0) candidates.filter { it.libraryId.contains("tricep") }.take(1)
                else if (abc && m == Muscle.ARMS) candidates.filter { it.libraryId.contains("curl") }.take(1)
                else candidates.take(if (m == Muscle.CARDIO) 1 else if (muscles.size == 1) 3 else 2)
            }
            Routine(UUID.randomUUID().toString(), he,
                selected.map { fresh(it).copy(sets = if (it.measure == Measure.MINUTES) 1 else 3) }, setOf(scheduled[index]), nameEn = en, customName = false,
                shortLabel = if (abc) ('A' + index).toString() else "")
        }
    }
}
