package com.streambert.tv.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TMDB_IMG_BASE = "https://image.tmdb.org/t/p"

fun tmdbImage(path: String?, size: String = "w500"): String? =
    path?.let { "$TMDB_IMG_BASE/$size$it" }

@Serializable
data class TmdbPage<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
    @SerialName("total_results") val totalResults: Int = 0
)

/**
 * Unified result item used in trending/search/recommendation lists. TMDB
 * returns `title`/`release_date` for movies and `name`/`first_air_date` for
 * TV, so both pairs are nullable here.
 */
@Serializable
data class TmdbResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_type") val mediaTypeRaw: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    val popularity: Double = 0.0
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.ifBlank { null }
}

@Serializable
data class TmdbGenre(val id: Int, val name: String)

@Serializable
data class ExternalIds(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null
)

@Serializable
data class ProvidersResponse(
    val results: List<ProviderInfo> = emptyList()
)

@Serializable
data class ProviderInfo(
    @SerialName("provider_id") val providerId: Int = 0,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("logo_path") val logoPath: String? = null
)

@Serializable
data class VideosResponse(
    val results: List<VideoInfo> = emptyList()
)

@Serializable
data class VideoInfo(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean = false,
    val name: String? = null
)

@Serializable
data class CreditsResponse(
    val cast: List<CastMember> = emptyList(),
    val crew: List<CrewMember> = emptyList()
)

@Serializable
data class CastMember(
    val id: Int = 0,
    val name: String? = null,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 999
)

@Serializable
data class CrewMember(
    val id: Int = 0,
    val name: String? = null,
    val job: String? = null,
    @SerialName("profile_path") val profilePath: String? = null
)

/** From GET /person/{id} — used as the title of the filmography screen. */
@Serializable
data class PersonDetails(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("profile_path") val profilePath: String? = null
)

/** From GET /person/{id}/combined_credits — the person's movie + TV roles. */
@Serializable
data class PersonCombinedCredits(
    val cast: List<TmdbResult> = emptyList(),
    val crew: List<TmdbResult> = emptyList()
)

@Serializable
data class MovieDetails(
    val id: Int,
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    // Populated when requested via append_to_response=release_dates.
    @SerialName("release_dates") val releaseDates: ReleaseDatesResponse? = null
) {
    val year: String? get() = releaseDate?.take(4)

    /** US theatrical/content certification (e.g. "PG-13"), if available. */
    val certification: String?
        get() {
            val results = releaseDates?.results ?: return null
            val country = results.firstOrNull { it.country == "US" } ?: results.firstOrNull()
            return country?.releaseDates
                ?.firstOrNull { it.certification.isNotBlank() }
                ?.certification
        }
}

@Serializable
data class TvDetails(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int = 0,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("origin_country") val originCountry: List<String> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    // Populated when requested via append_to_response=content_ratings.
    @SerialName("content_ratings") val contentRatings: ContentRatingsResponse? = null
) {
    val year: String? get() = firstAirDate?.take(4)

    /** US TV content rating (e.g. "TV-PG"), if available. */
    val certification: String?
        get() {
            val results = contentRatings?.results ?: return null
            return (results.firstOrNull { it.country == "US" && it.rating.isNotBlank() }
                ?: results.firstOrNull { it.rating.isNotBlank() })?.rating
        }
}

@Serializable
data class ContentRatingsResponse(
    val results: List<ContentRatingEntry> = emptyList()
)

@Serializable
data class ContentRatingEntry(
    @SerialName("iso_3166_1") val country: String = "",
    val rating: String = ""
)

@Serializable
data class ReleaseDatesResponse(
    val results: List<ReleaseDateCountry> = emptyList()
)

@Serializable
data class ReleaseDateCountry(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<ReleaseDateEntry> = emptyList()
)

@Serializable
data class ReleaseDateEntry(
    val certification: String = ""
)

@Serializable
data class SeasonSummary(
    val id: Int,
    val name: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null
)

@Serializable
data class SeasonDetails(
    val id: Int,
    val name: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    val episodes: List<Episode> = emptyList()
)

@Serializable
data class Episode(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0
)
