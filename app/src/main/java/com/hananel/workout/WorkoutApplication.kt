package com.hananel.workout

import android.app.Application
import com.hananel.workout.data.WorkoutRepository
import com.hananel.workout.data.WorkoutCoordinator
import com.hananel.workout.data.AndroidEffects
import com.hananel.workout.timer.Notifications

class WorkoutApplication : Application() {
    val repository by lazy { WorkoutRepository(this) }
    val coordinator by lazy { WorkoutCoordinator(repository, AndroidEffects(this)) }
    override fun onCreate() {
        super.onCreate()
        Notifications.channels(this)
    }
}
