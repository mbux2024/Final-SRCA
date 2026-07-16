package com.streambert.tv.data.watched

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchedStore: DataStore<Preferences> by preferencesDataStore(name = "streambert_watched")

/**
 * Tracks watched state, keyed per item: movies by `movie_<id>` and individual
 * episodes by `tv_<id>_s<season>e<episode>`.
 */
class WatchedRepository(private val context: Context) {

    private val KEY = stringSetPreferencesKey("watched_keys")

    /** All watched keys. */
    val watched: Flow<Set<String>> = context.watchedStore.data.map { it[KEY] ?: emptySet() }

    suspend fun toggle(key: String) {
        context.watchedStore.edit { prefs ->
            val current = prefs[KEY]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(key)) current.remove(key)
            prefs[KEY] = current
        }
    }

    suspend fun setWatched(key: String, watched: Boolean) {
        context.watchedStore.edit { prefs ->
            val current = prefs[KEY]?.toMutableSet() ?: mutableSetOf()
            if (watched) current.add(key) else current.remove(key)
            prefs[KEY] = current
        }
    }

    companion object {
        fun movieKey(id: Int) = "movie_$id"
        fun episodeKey(id: Int, season: Int, episode: Int) = "tv_${id}_s${season}e$episode"
    }
}
