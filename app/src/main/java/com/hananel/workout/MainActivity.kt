package com.hananel.workout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hananel.workout.ui.WorkoutApp

class MainActivity : ComponentActivity() {
    private val timerRequest = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("openTimer", false)) timerRequest.intValue++
        setContent { WorkoutApp(viewModel(), timerRequest.intValue) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("openTimer", false)) timerRequest.intValue++
    }
}
