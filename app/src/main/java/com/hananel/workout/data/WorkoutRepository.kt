package com.hananel.workout.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.hananel.workout.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

object StateSerializer : Serializer<AppState> {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val defaultValue = AppState()
    override suspend fun readFrom(input: InputStream): AppState = try {
        json.decodeFromString<AppState>(input.readBytes().decodeToString()).validate()
    } catch (e: SerializationException) {
        throw CorruptionException("Cannot decode workout data; the original file has been retained", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Workout data validation failed; the original file has been retained", e)
    }
    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}

private val Context.workoutStore: DataStore<AppState> by dataStore("workout-v3.json", StateSerializer)

class WorkoutRepository(private val store: DataStore<AppState>) {
    constructor(context: Context) : this(context.applicationContext.workoutStore)
    val data get() = store.data
    suspend fun current() = data.first()
    suspend fun update(transform: (AppState) -> AppState) = store.updateData(transform)
    suspend fun claimAlert(clock: ClockReading, timerId: String): RestTimer? {
        var claimed: RestTimer? = null
        update { state ->
            val timer = state.rest
            if (timer != null && timer.id == timerId && !timer.alerted && timer.pausedMs == null && timer.remainingMs(clock) == 0L) {
                claimed = timer
                state.copy(rest = timer.copy(alerted = true))
            } else state
        }
        return claimed
    }
    suspend fun export(): String = StateSerializer.json.encodeToString(current())
    fun parseBackup(text: String): AppState = StateSerializer.json.decodeFromString<AppState>(text).validate()
}

fun Context.clock() = ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(),
    Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1), LocalDate.now())
