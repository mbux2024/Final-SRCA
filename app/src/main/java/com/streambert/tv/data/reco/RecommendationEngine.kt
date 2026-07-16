package com.streambert.tv.data.reco

import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.tmdb.Genres
import com.streambert.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds personalized, Netflix-style recommendation rows from the user's own
 * signals — what they're currently watching, what they've saved to My List, and
 * what they've finished.
 *
 * The engine is content-based and collaborative-ish via TMDB's own
 * "recommendations" graph:
 *
 *  1. **Recommended for You** — recommendations across all seed titles, blended
 *     and re-ranked. A candidate that is recommended by several of your seeds
 *     (and is well-rated) floats to the top; recency of the seed adds weight.
 *  2. **Because you watched {Title}** — the raw recommendations for your most
 *     recent / highest-signal seeds, one row each.
 *  3. **{Genre} You Might Like** — discover rows for the genres you engage with
 *     most, computed as a genre-affinity histogram.
 *
 * Everything the user has already seen, saved, or is mid-watch on is excluded so
 * rows stay fresh. All calls hit TMDB only (no MDBList quota).
 */
class RecommendationEngine(private val repo: TmdbRepository) {

    /** A title the user has engaged with, plus how strong that signal is. */
    data class Seed(
        val id: Int,
        val type: MediaType,
        val title: String,
        val weight: Double
    )

    suspend fun buildRows(
        seeds: List<Seed>,
        exclude: Set<String>,
        myList: List<CatalogItem>
    ): List<CatalogRow> = coroutineScope {
        if (seeds.isEmpty()) return@coroutineScope emptyList()

        val topSeeds = seeds.take(MAX_SEEDS)

        // Fetch each seed's recommendations in parallel; tolerate per-seed failure.
        val recsBySeed: List<Pair<Seed, List<CatalogItem>>> = topSeeds.map { seed ->
            async {
                seed to runCatching {
                    repo.recommendationsFor(
                        CatalogItem(seed.id, seed.type, seed.title, null, null, null, 0.0, null)
                    )
                }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val rows = mutableListOf<CatalogRow>()
        val seedKeys = topSeeds.map { key(it.type, it.id) }.toSet()

        // ── 1) Aggregated "Recommended for You" ───────────────────────────────
        // Score = Σ (seedWeight · positionDecay) + small rating bonus. Titles
        // recommended by multiple seeds accumulate the most score.
        val agg = LinkedHashMap<String, Scored>()
        recsBySeed.forEach { (seed, recs) ->
            recs.forEachIndexed { idx, item ->
                val k = key(item)
                if (k in exclude || k in seedKeys || item.posterUrl == null) return@forEachIndexed
                val positionDecay = 1.0 / (1.0 + idx * 0.12)
                val add = seed.weight * positionDecay + item.rating * 0.02
                val cur = agg[k]
                if (cur == null) {
                    agg[k] = Scored(item, add, 1)
                } else {
                    cur.score += add
                    cur.hits += 1
                }
            }
        }
        val recommended = agg.values
            .sortedWith(compareByDescending<Scored> { it.hits }.thenByDescending { it.score })
            .map { it.item }
            .take(24)
        if (recommended.size >= MIN_ROW) rows += CatalogRow("Recommended for You", recommended)

        // ── 2) "Because you watched {Title}" ──────────────────────────────────
        recsBySeed
            .filter { it.first.title.isNotBlank() }
            .take(MAX_BECAUSE_ROWS)
            .forEach { (seed, recs) ->
                val list = recs
                    .filter { key(it) !in exclude && it.posterUrl != null }
                    .distinctBy { key(it) }
                    .take(20)
                if (list.size >= MIN_ROW) {
                    rows += CatalogRow("Because you watched ${seed.title}", list)
                }
            }

        // ── 3) Genre-affinity discover rows ───────────────────────────────────
        // Genre ids differ between movie and TV, so we work within the media
        // type the user engages with most and only count same-type genre ids.
        val preferTv = seeds.count { it.type == MediaType.TV } >= seeds.count { it.type == MediaType.MOVIE }
        val wantType = if (preferTv) MediaType.TV else MediaType.MOVIE
        val genreScore = HashMap<Int, Int>()
        myList.filter { it.type == wantType }.forEach { m ->
            m.genreIds.forEach { g -> genreScore[g] = (genreScore[g] ?: 0) + 2 }
        }
        agg.values.forEach { s ->
            if (s.item.type == wantType) s.item.genreIds.forEach { g -> genreScore[g] = (genreScore[g] ?: 0) + 1 }
        }
        val topGenres = genreScore.entries
            .sortedByDescending { it.value }
            .mapNotNull { e -> Genres.nameFor(e.key)?.let { e.key } }
            .take(MAX_GENRE_ROWS)

        topGenres.forEach { gid ->
            val name = Genres.nameFor(gid) ?: return@forEach
            val disc = runCatching {
                if (wantType == MediaType.TV) repo.tvByGenre(gid, minVotes = 200)
                else repo.moviesByGenre(gid, minVotes = 200)
            }.getOrDefault(emptyList())
                .filter { key(it) !in exclude && it.posterUrl != null }
                .distinctBy { key(it) }
                .take(20)
            if (disc.size >= MIN_ROW) rows += CatalogRow("$name You Might Like", disc)
        }

        rows
    }

    private class Scored(val item: CatalogItem, var score: Double, var hits: Int)

    private fun key(item: CatalogItem) = key(item.type, item.id)
    private fun key(type: MediaType, id: Int) = "${type}_$id"

    companion object {
        const val MAX_SEEDS = 6
        const val MAX_BECAUSE_ROWS = 3
        const val MAX_GENRE_ROWS = 2
        const val MIN_ROW = 4
    }
}
