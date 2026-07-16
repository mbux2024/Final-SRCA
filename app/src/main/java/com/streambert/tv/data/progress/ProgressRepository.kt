package com.streambert.tv.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.progressStore: DataStore<Preferences> by preferencesDataStore(name = "streambert_progress")

/**
 * Persists playback position per title/episode for resume + continue-watching.
 * Stored as a single JSON list in DataStore.
 */
class ProgressRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("entries")

    private fun decode(raw: String?): List<WatchProgress> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<WatchProgress>>(raw) }.getOrDefault(emptyList())

    /** All entries (unfiltered), newest first. */
    val all: Flow<List<WatchProgress>> = context.progressStore.data.map { prefs ->
        decode(prefs[KEY]).sortedByDescending { it.updatedAt }
    }

    /**
     * Continue-watching list: only partially-watched items from the last 30
     * days, de-duplicated to the latest episode per show, newest first.
     */
    val continueWatching: Flow<List<WatchProgress>> = all.map { list ->
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        list.asSequence()
            .filter { it.updatedAt >= cutoff }
            // Partially-watched items, OR a "next episode up" placeholder.
            .filter { it.nextUp || (it.positionMs >= MIN_RESUME_MS && it.fraction < FINISHED_FRACTION) }
            .distinctBy { "${it.type}_${it.tmdbId}" }
            .toList()
    }

    suspend fun get(key: String): WatchProgress? =
        all.first().firstOrNull { it.key == key }

    /**
     * Upsert progress. If the item is essentially finished it is removed so it
     * drops out of Continue Watching.
     */
    suspend fun save(entry: WatchProgress) {
        context.progressStore.edit { prefs ->
            val current = decode(prefs[KEY]).filterNot { it.key == entry.key }
            val finished = entry.durationMs > 0 &&
                entry.positionMs.toFloat() / entry.durationMs >= FINISHED_FRACTION
            val next = if (finished) current else current + entry
            // Cap stored entries to keep the blob small.
            val trimmed = next.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
            prefs[KEY] = json.encodeToString(trimmed)
        }
    }

    suspend fun remove(key: String) {
        context.progressStore.edit { prefs ->
            val next = decode(prefs[KEY]).filterNot { it.key == key }
            prefs[KEY] = json.encodeToString(next)
        }
    }

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
        private const val MIN_RESUME_MS = 30_000L      // ignore the first 30s
        private const val FINISHED_FRACTION = 0.92f
        private const val MAX_ENTRIES = 100
    }
}
