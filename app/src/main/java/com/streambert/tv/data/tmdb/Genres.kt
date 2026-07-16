package com.streambert.tv.data.tmdb

/**
 * A browsable genre shown as a landscape cover-art card, mapping to TMDB
 * movie/TV genre ids so its screen can discover both.
 *
 * Cover images are referenced from public image repos (same source used by
 * similar TV apps) so we don't bundle artwork; a null id means that media type
 * doesn't have a matching TMDB genre and is simply skipped for that row.
 */
data class Genre(
    val name: String,
    val coverUrl: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?
)

object Genres {
    private const val BASE =
        "https://raw.githubusercontent.com/chrishudson918/images/main/Landscape%20Genres/"
    private const val FUSION =
        "https://raw.githubusercontent.com/itsrenoria/fusion-starter-kit/refs/heads/main/resources/widgets/genres/wide/dannyrutledge/"

    val ALL = listOf(
        Genre("Action", "${BASE}ACTION.jpegli.jpg", movieGenreId = 28, tvGenreId = 10759),
        Genre("Comedy", "${BASE}COMEDY.jpegli.jpg", movieGenreId = 35, tvGenreId = 35),
        Genre("Sci-Fi", "${BASE}SCI%20FI.jpegli.jpg", movieGenreId = 878, tvGenreId = 10765),
        Genre("Thriller", "${BASE}THRILLER.jpegli.jpg", movieGenreId = 53, tvGenreId = null),
        Genre("Drama", "${BASE}DRAMA.jpegli.jpg", movieGenreId = 18, tvGenreId = 18),
        Genre("Horror", "${BASE}HORROR.jpegli.jpg", movieGenreId = 27, tvGenreId = null),
        Genre("Documentary", "${BASE}DOC.jpegli.jpg", movieGenreId = 99, tvGenreId = 99),
        Genre("Romance", "${BASE}ROMANCE.jpegli.jpg", movieGenreId = 10749, tvGenreId = null),
        Genre("Animation", "${BASE}ANIMATION.jpegli.jpg", movieGenreId = 16, tvGenreId = 16),
        Genre("Family", "${BASE}KIDS%20AND%20FAMILY.jpegli.jpg", movieGenreId = 10751, tvGenreId = 10751),
        Genre("Fantasy", "${FUSION}fantasy-wide.png", movieGenreId = 14, tvGenreId = 10765),
        Genre("Adventure", "${FUSION}adventure-wide.png", movieGenreId = 12, tvGenreId = 10759),
        Genre("War & Military", "${FUSION}war-stories-wide.png", movieGenreId = 10752, tvGenreId = 10768)
    )

    /** Canonical TMDB genre-id → display-name map (movie + TV genres combined). */
    private val ID_TO_NAME: Map<Int, String> = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10763 to "News",
        10764 to "Reality",
        10765 to "Sci-Fi & Fantasy",
        10766 to "Soap",
        10767 to "Talk",
        10768 to "War & Politics"
    )

    fun nameFor(id: Int): String? = ID_TO_NAME[id]

    /** Up to [max] distinct display names for the given TMDB genre ids. */
    fun namesFor(ids: List<Int>, max: Int = 2): List<String> =
        ids.mapNotNull { nameFor(it) }.distinct().take(max)
}
