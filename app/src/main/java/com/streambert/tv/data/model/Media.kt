package com.streambert.tv.data.model

import com.streambert.tv.data.tmdb.TmdbResult
import kotlinx.serialization.Serializable

/** Whether a piece of content is a movie or a TV series. */
@Serializable
enum class MediaType(val tmdb: String) {
    MOVIE("movie"),
    TV("tv");

    companion object {
        fun from(raw: String?): MediaType =
            if (raw.equals("tv", ignoreCase = true)) TV else MOVIE
    }
}

/**
 * Lightweight, UI-friendly representation of a catalog item used across rows,
 * cards and detail navigation. Built from a [TmdbResult].
 */
@Serializable
data class CatalogItem(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val year: String?,
    /** TMDB genre ids from the list response, used for the Netflix-style meta line. */
    val genreIds: List<Int> = emptyList()
)

/** A named cast/crew person for the detail-page Cast row. */
data class CastPerson(
    val id: Int,
    val name: String,
    val role: String,
    val imageUrl: String?
)

/** A named horizontal row on the home screen. */
data class CatalogRow(
    val title: String,
    val items: List<CatalogItem>,
    val ranked: Boolean = false
)
