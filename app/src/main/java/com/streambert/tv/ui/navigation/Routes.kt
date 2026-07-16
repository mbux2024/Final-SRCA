package com.streambert.tv.ui.navigation

import android.net.Uri
import com.streambert.tv.data.model.MediaType

/** Centralised navigation route definitions and builders. */
object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

    const val DETAIL = "detail/{type}/{id}"
    fun detail(type: MediaType, id: Int) = "detail/${type.tmdb}/$id"

    const val SERVICE = "service/{id}/{name}"
    fun service(id: Int, name: String) = "service/$id/${Uri.encode(name)}"

    const val PERSON = "person/{id}/{name}"
    fun person(id: Int, name: String) = "person/$id/${Uri.encode(name.ifBlank { "Cast" })}"

    // media = "movie" | "tv" | "all" — restricts the genre screen to one media
    // type (so the Movies tab shows only movies, the Shows tab only TV).
    const val GENRE = "genre/{name}/{movieId}/{tvId}?media={media}"
    fun genre(name: String, movieId: Int, tvId: Int, media: String = "all") =
        "genre/${Uri.encode(name)}/$movieId/$tvId?media=$media"

    const val TRAILER = "trailer/{key}"
    fun trailer(key: String) = "trailer/${Uri.encode(key)}"

    // season/episode are -1 for movies; streamUrl is a pre-resolved direct URL
    // (from the sources panel) or empty to auto-resolve the best source.
    const val PLAYER = "player/{type}/{id}/{season}/{episode}?title={title}&stream={stream}&poster={poster}&backdrop={backdrop}&hash={hash}&debrid={debrid}"
    fun player(
        type: MediaType,
        id: Int,
        season: Int = -1,
        episode: Int = -1,
        title: String = "",
        streamUrl: String = "",
        posterUrl: String = "",
        backdropUrl: String = "",
        hash: String = "",
        debrid: String = ""
    ): String {
        val t = Uri.encode(title)
        val s = Uri.encode(streamUrl)
        val p = Uri.encode(posterUrl)
        val b = Uri.encode(backdropUrl)
        val h = Uri.encode(hash)
        val d = Uri.encode(debrid)
        return "player/${type.tmdb}/$id/$season/$episode?title=$t&stream=$s&poster=$p&backdrop=$b&hash=$h&debrid=$d"
    }
}
