package com.streambert.tv.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryStore: DataStore<Preferences> by
    preferencesDataStore(name = "streambert_search_history")

/**
 * Persisted "Recent searches" history. Stored as a newline-joined string so the
 * most-recent-first order is preserved (a Set wouldn't keep order). Entries are
 * de-duplicated case-insensitively and capped at [MAX].
 */
class SearchHistoryRepository(private val context: Context) {

    private val key = stringPreferencesKey("recent_searches")

    val history: Flow<List<String>> = context.searchHistoryStore.data.map { prefs ->
        prefs[key].toList()
    }

    /** Adds/promotes a query to the top of the history. */
    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        context.searchHistoryStore.edit { prefs ->
            val current = prefs[key].toList()
            val updated = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
            prefs[key] = updated.joinToString(SEP)
        }
    }

    /** Removes a single entry. */
    suspend fun remove(query: String) {
        val q = query.trim()
        context.searchHistoryStore.edit { prefs ->
            val updated = prefs[key].toList().filterNot { it.equals(q, ignoreCase = true) }
            prefs[key] = updated.joinToString(SEP)
        }
    }

    /** Clears the entire history. */
    suspend fun clear() {
        context.searchHistoryStore.edit { it.remove(key) }
    }

    private fun String?.toList(): List<String> =
        this?.split(SEP)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    companion object {
        private const val MAX = 20
        private const val SEP = "\n"
    }
}
