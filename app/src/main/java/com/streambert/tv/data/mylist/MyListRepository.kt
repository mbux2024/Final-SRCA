package com.streambert.tv.data.mylist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.myListStore: DataStore<Preferences> by preferencesDataStore(name = "streambert_mylist")

/**
 * Persists the user's "My List" (saved movies/series) as a JSON list, newest
 * first. Membership is keyed by type + tmdb id.
 */
class MyListRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("items")

    private fun decode(raw: String?): List<CatalogItem> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<CatalogItem>>(raw) }.getOrDefault(emptyList())

    /** All saved items, newest first (insertion order preserved). */
    val items: Flow<List<CatalogItem>> = context.myListStore.data.map { decode(it[KEY]) }

    /** Reactive membership check for a given item. */
    fun contains(type: MediaType, id: Int): Flow<Boolean> =
        items.map { list -> list.any { it.type == type && it.id == id } }

    suspend fun isSaved(type: MediaType, id: Int): Boolean =
        items.first().any { it.type == type && it.id == id }

    /** Add if absent, remove if present. Returns the new membership state. */
    suspend fun toggle(item: CatalogItem): Boolean {
        var nowSaved = false
        context.myListStore.edit { prefs ->
            val current = decode(prefs[KEY])
            val exists = current.any { it.type == item.type && it.id == item.id }
            val next = if (exists) {
                current.filterNot { it.type == item.type && it.id == item.id }
            } else {
                listOf(item) + current   // newest first
            }
            nowSaved = !exists
            prefs[KEY] = json.encodeToString(next)
        }
        return nowSaved
    }

    suspend fun remove(type: MediaType, id: Int) {
        context.myListStore.edit { prefs ->
            val next = decode(prefs[KEY]).filterNot { it.type == type && it.id == id }
            prefs[KEY] = json.encodeToString(next)
        }
    }
}
