package com.streambert.tv.data.stream

import android.util.Log
import com.streambert.tv.data.realdebrid.RealDebridRepository
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.data.torbox.TorBoxRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Aggregates playable streams from the user's configured sources:
 *  - **Debrid-backed Torrentio** (TorBox and/or Real-Debrid): returns
 *    already-resolved direct URLs.
 *  - **Scraper addons** (Torrentio no-debrid, Comet): return torrent hashes,
 *    which are resolved on play through the user's debrid service — **TorBox**
 *    (instant-checked via checkcached) is tried first, then **Real-Debrid**
 *    (addMagnet → selectFiles → unrestrict) as a fallback.
 *
 * Everything is merged, de-duplicated, and sorted instant-first.
 */
class StreamRepository(
    private val api: StremioApi,
    private val torbox: TorBoxRepository,
    private val realDebrid: RealDebridRepository,
    private val settings: SettingsRepository
) {

    suspend fun resolveStream(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null,
        onProgress: (String) -> Unit = {}
    ): StreamResolution {
        if (settings.activeAddonSources().isEmpty()) {
            return StreamResolution.Failure(
                "No debrid configured. Add your TorBox and/or Real-Debrid key in Settings."
            )
        }
        onProgress("Finding best source…")
        val options = runCatching { buildOptions(imdbId, season, episode) }.getOrNull()
        if (options.isNullOrEmpty()) {
            return StreamResolution.Failure(
                "No cached sources found yet. Try another title/quality or play again shortly."
            )
        }
        // Auto-pick: the list is already sorted by score (4K cap + 5.1 preference
        // + cached-first). Take the first valid option that has a URL or hash.
        val best = options.firstOrNull { it.quality <= 2160 } ?: options.firstOrNull()
            ?: return StreamResolution.Failure("No compatible source found within quality limits.")

        // Direct (debrid) sources already have a URL; scraper sources resolve now.
        best.url?.let { return StreamResolution.Ready(it, best.label) }
        best.hash?.let {
            onProgress("Preparing instant stream…")
            return resolveViaDebrid(it, best.label, season, episode, best.debrid)
        }
        return StreamResolution.Failure("Selected source has no URL or hash.")
    }

    /**
     * Resolve a specific chosen source (from the picker) to a playable URL.
     * [debrid] pins it to a service ("TorBox"/"RD") the user picked; null lets
     * it fall back across whatever is configured.
     */
    suspend fun resolveHash(
        hash: String,
        title: String,
        season: Int?,
        episode: Int?,
        debrid: String? = null
    ): StreamResolution = resolveViaDebrid(hash, title, season, episode, debrid)

    /**
     * Resolve a torrent hash to a playable URL through the configured debrid
     * service(s): TorBox first (fast instant check + CDN), then Real-Debrid as
     * a fallback. Whichever has the release cached plays instantly.
     */
    private suspend fun resolveViaDebrid(
        hash: String,
        title: String,
        season: Int?,
        episode: Int?,
        debrid: String? = null
    ): StreamResolution {
        val hasTorBox = settings.currentTorboxKey().isNotBlank()
        val hasRealDebrid = settings.currentRealDebridKey().isNotBlank()
        // Honour an explicit picker choice — play through exactly that service.
        when (debrid) {
            SettingsRepository.DEBRID_TORBOX ->
                if (hasTorBox) return torbox.resolveHash(hash, title, season, episode)
            SettingsRepository.DEBRID_RD ->
                if (hasRealDebrid) return realDebrid.resolveHash(hash, title, season, episode)
        }
        // Auto / fallback: TorBox first (fast instant check + CDN), then RD.
        if (hasTorBox) {
            val result = torbox.resolveHash(hash, title, season, episode)
            if (result is StreamResolution.Ready || !hasRealDebrid) return result
        }
        if (hasRealDebrid) {
            return realDebrid.resolveHash(hash, title, season, episode)
        }
        return StreamResolution.Failure(
            "No debrid configured. Add a TorBox or Real-Debrid key in Settings."
        )
    }

    /** All playable streams, instant-first then by quality, for the picker. */
    suspend fun listStreams(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null
    ): List<StreamOption> = try {
        buildOptions(imdbId, season, episode)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Incremental stream loading — emits partial results as each addon/provider
     * completes, without waiting for all to finish. Each emission is the full
     * accumulated, deduplicated, scored list so far.
     *
     * The flow completes once all providers have finished or timed out.
     */
    fun listStreamsFlow(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null
    ): Flow<StreamLoadingState> = flow {
        val sources = settings.activeAddonSources()
        if (sources.isEmpty()) {
            emit(StreamLoadingState(emptyList(), allDone = true, error = "No addons configured."))
            return@flow
        }

        val type = if (season != null && episode != null) "series" else "movie"
        val id = if (type == "series") "$imdbId:$season:$episode" else imdbId
        val preferred = qualityToInt(settings.currentPreferredQuality())
        val hasTorBox = settings.currentTorboxKey().isNotBlank()
        val hasRealDebrid = settings.currentRealDebridKey().isNotBlank()
        val filters = settings.currentStreamFilters()

        val accumulated = mutableListOf<StreamOption>()
        val seenKeys = HashSet<String>()
        var providersRemaining = sources.size
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "┌─ Source loading started: ${sources.size} provider(s) for $type/$id")

        // Emit initial loading state
        emit(StreamLoadingState(emptyList(), allDone = false, providersTotal = sources.size, providersComplete = 0))

        supervisorScope {
            val jobs = sources.map { src ->
                async {
                    val providerStart = System.currentTimeMillis()
                    Log.d(TAG, "│  ▶ [${src.label}] started")

                    val result = withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        runCatching {
                            val raw = api.getStreams("${src.baseUrl}stream/$type/$id.json").streams
                            val streams = raw.filter {
                                if (src.resolveViaTorBox) hashOf(it) != null else !it.url.isNullOrBlank()
                            }
                            if (streams.isEmpty()) return@runCatching emptyList<StreamOption>()

                            val hashByStream = streams.associateWith { hashOf(it) }
                            val cached = if (src.isTorBox && hasTorBox)
                                torbox.instantHashes(hashByStream.values.filterNotNull().distinct())
                            else emptySet()

                            val options = mutableListOf<StreamOption>()
                            streams.forEach { s ->
                                val text = "${s.name.orEmpty()} ${s.title.orEmpty()}"
                                val q = parseQuality(text.lowercase(Locale.ROOT))
                                val hash = hashByStream[s]

                                fun option(debrid: String?, instant: Boolean) = StreamOption(
                                    url = if (src.resolveViaTorBox) null else s.url,
                                    hash = hash,
                                    label = s.label,
                                    qualityLabel = qualityLabel(q),
                                    quality = q,
                                    cached = instant,
                                    instant = instant,
                                    provider = src.label,
                                    debrid = debrid,
                                    badges = ReleaseBadges.parse(text),
                                    sizeLabel = parseSize(text),
                                    container = parseContainer(text),
                                    language = parseLanguage(text),
                                    seeders = parseSeeders(text)
                                )

                                if (src.resolveViaTorBox) {
                                    if (hasTorBox) options.add(option(SettingsRepository.DEBRID_TORBOX, hash != null && hash in cached))
                                    if (hasRealDebrid) options.add(option(SettingsRepository.DEBRID_RD, markerCached(s)))
                                } else {
                                    val instant = if (src.isTorBox) (hash != null && hash in cached) else markerCached(s)
                                    options.add(option(src.debrid, instant))
                                }
                            }
                            options
                        }.getOrDefault(emptyList())
                    }

                    val elapsed = System.currentTimeMillis() - providerStart
                    ProviderResult(src.label, result, elapsed)
                }
            }

            // Collect results as each provider completes (not in order, but as they finish)
            for (job in jobs) {
                val result = job.await()
                providersRemaining--
                val providersComplete = sources.size - providersRemaining

                if (result.options == null) {
                    Log.d(TAG, "│  ✗ [${result.providerLabel}] TIMED OUT after ${result.elapsedMs}ms")
                } else if (result.options.isEmpty()) {
                    Log.d(TAG, "│  ○ [${result.providerLabel}] returned 0 sources (${result.elapsedMs}ms)")
                } else {
                    Log.d(TAG, "│  ✓ [${result.providerLabel}] returned ${result.options.size} sources (${result.elapsedMs}ms)")

                    // Deduplicate and add new sources
                    val newOptions = result.options.filter { opt ->
                        val key = "${opt.hash ?: opt.url}_${opt.debrid}"
                        seenKeys.add(key) // returns false if already existed
                    }
                    if (newOptions.isNotEmpty()) {
                        accumulated.addAll(newOptions)
                    }
                }

                // Apply filters + sort, then emit current state
                val filtered = filters.applyTo(accumulated)
                val sorted = filtered
                    .distinctBy { "${it.hash ?: it.url}_${it.debrid}" }
                    .sortedByDescending { score(it, preferred) }

                val isFirstResult = providersComplete == 1 && sorted.isNotEmpty()
                if (isFirstResult) {
                    val firstSourceTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "│  ★ First source available at ${firstSourceTime}ms")
                }

                emit(StreamLoadingState(
                    sources = sorted,
                    allDone = providersRemaining == 0,
                    providersTotal = sources.size,
                    providersComplete = providersComplete
                ))
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "└─ Source loading complete: ${accumulated.size} total sources in ${totalTime}ms")
    }

    /** State emitted by [listStreamsFlow] as providers complete incrementally. */
    data class StreamLoadingState(
        val sources: List<StreamOption>,
        val allDone: Boolean,
        val error: String? = null,
        val providersTotal: Int = 0,
        val providersComplete: Int = 0
    )

    private data class ProviderResult(
        val providerLabel: String,
        val options: List<StreamOption>?,  // null = timed out
        val elapsedMs: Long
    )

    companion object {
        private const val TAG = "StreamRepo"
        private const val PER_PROVIDER_TIMEOUT_MS = 20_000L  // 20s per provider
    }

    // ── Core ─────────────────────────────────────────────────────────────
    private suspend fun buildOptions(
        imdbId: String,
        season: Int?,
        episode: Int?
    ): List<StreamOption> {
        val sources = settings.activeAddonSources()
        if (sources.isEmpty()) return emptyList()

        val type = if (season != null && episode != null) "series" else "movie"
        val id = if (type == "series") "$imdbId:$season:$episode" else imdbId
        val preferred = qualityToInt(settings.currentPreferredQuality())

        val all = mutableListOf<StreamOption>()
        val hasTorBox = settings.currentTorboxKey().isNotBlank()
        val hasRealDebrid = settings.currentRealDebridKey().isNotBlank()
        for (src in sources) {
            val raw = runCatching {
                api.getStreams("${src.baseUrl}stream/$type/$id.json").streams
            }.getOrDefault(emptyList())

            // Debrid sources need a resolved url; scraper sources need a hash.
            val streams = raw.filter {
                if (src.resolveViaTorBox) hashOf(it) != null else !it.url.isNullOrBlank()
            }
            if (streams.isEmpty()) continue

            val hashByStream = streams.associateWith { hashOf(it) }
            // TorBox instant-availability (used by scraper + torbox-direct sources).
            val cached = if (src.isTorBox && hasTorBox)
                torbox.instantHashes(hashByStream.values.filterNotNull().distinct())
            else emptySet()

            streams.forEach { s ->
                val text = "${s.name.orEmpty()} ${s.title.orEmpty()}"
                val q = parseQuality(text.lowercase(Locale.ROOT))
                val hash = hashByStream[s]

                fun option(debrid: String?, instant: Boolean) = StreamOption(
                    url = if (src.resolveViaTorBox) null else s.url,
                    hash = hash,
                    label = s.label,
                    qualityLabel = qualityLabel(q),
                    quality = q,
                    cached = instant,
                    instant = instant,
                    provider = src.label,
                    debrid = debrid,
                    badges = ReleaseBadges.parse(text),
                    sizeLabel = parseSize(text),
                    container = parseContainer(text),
                    language = parseLanguage(text),
                    seeders = parseSeeders(text)
                )

                if (src.resolveViaTorBox) {
                    // Scraper hash source — offer it through EACH connected debrid
                    // so TorBox and Real-Debrid show up as separate options.
                    if (hasTorBox) all.add(option(SettingsRepository.DEBRID_TORBOX, hash != null && hash in cached))
                    if (hasRealDebrid) all.add(option(SettingsRepository.DEBRID_RD, markerCached(s)))
                } else {
                    // Direct URL already resolved by a specific debrid service.
                    val instant = if (src.isTorBox) (hash != null && hash in cached) else markerCached(s)
                    all.add(option(src.debrid, instant))
                }
            }
        }

        // Nuvio-style feature filtering (Dolby Vision / HDR / codec / min-quality).
        // Applied as a hard constraint, exactly like Nuvio's stream filters — the
        // app simply won't offer/auto-pick releases the user filtered out.
        val filtered = settings.currentStreamFilters().applyTo(all)

        return filtered
            // Keep TorBox and RD variants of the same release as separate options.
            .distinctBy { "${it.hash ?: it.url}_${it.debrid}" }
            .sortedByDescending { score(it, preferred) }
    }

    private fun hashOf(stream: StremioStream): String? {
        stream.infoHash?.takeIf { it.length == 40 }?.let { return it.lowercase(Locale.ROOT) }
        val m = Regex("[a-fA-F0-9]{40}").find(stream.url.orEmpty())
        return m?.value?.lowercase(Locale.ROOT)
    }

    private fun markerCached(stream: StremioStream): Boolean {
        val text = "${stream.name.orEmpty()} ${stream.title.orEmpty()}".lowercase(Locale.ROOT)
        return stream.name?.contains("+") == true ||
            stream.name?.contains("⚡") == true ||
            text.contains("cached")
    }

    /**
     * Ranking for auto-picking the best playable source for autoplay. Priority:
     *   1. HARD CONSTRAINTS: must be ≤4K video, prefer ≤5.1 audio.
     *      Sources exceeding 4K are excluded entirely. Sources with >5.1 audio
     *      (Atmos, TrueHD, DTS:X, 7.1) are penalized but not excluded (may be
     *      the only option available).
     *   2. Cached/instant on debrid (streams immediately, no download wait).
     *   3. Highest quality within the 4K ceiling (prefer 4K > 1080p > 720p).
     *   4. Prefer 5.1 audio over higher formats (Atmos/7.1 are penalized).
     *   5. Most seeders (fastest for the debrid to serve / least likely to stall).
     * CAM/telesync rips are pushed to the bottom.
     */
    private fun score(option: StreamOption, preferredQuality: Int): Long {
        var s = 0L

        // ── HARD CONSTRAINT: never pick anything above 4K ──
        if (option.quality > 2160) return -10_000_000L

        // ── Audio penalty: prefer 5.1 or lower, penalize >5.1 formats ──
        val badges = option.badges.map { it.lowercase(java.util.Locale.ROOT) }
        val hasHighAudio = badges.any { it == "atmos" || it == "truehd" || it == "dts:x" || it == "dts-hd" || it == "7.1" }
        val has51 = badges.any { it == "5.1" }
        if (hasHighAudio && !has51) s -= 50_000  // penalize >5.1 audio (still selectable as fallback)

        // (1) Cached first — by far the biggest speed factor for autoplay.
        if (option.instant) s += 1_000_000

        // (2) Highest quality within the 4K ceiling (prefer higher).
        // Use preferredQuality as target: pick closest to it but never above 4K.
        val effectivePreferred = preferredQuality.coerceAtMost(2160)
        s += (100_000 - kotlin.math.abs(option.quality - effectivePreferred) * 40).coerceAtLeast(0).toLong()

        // (3) Bonus for having 5.1 audio (the preferred format).
        if (has51) s += 10_000

        // (4) Most seeders — capped so it only breaks ties (max +5000).
        s += (option.seeders.coerceIn(0, 1000) * 5).toLong()

        // Push garbage rips to the bottom.
        val lower = option.label.lowercase(java.util.Locale.ROOT)
        if (lower.contains("cam") || lower.contains("hdts") || lower.contains("telesync")) s -= 200_000
        return s
    }

    /** Seeder count from the release title. Torrentio/Comet use "👤 123";
     *  falls back to "Seeders: 123" style. Returns 0 when unknown. */
    private fun parseSeeders(text: String): Int {
        Regex("\uD83D\uDC64\\s*(\\d+)").find(text)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }
        Regex("(?:seeders?|seeds)\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            .find(text)?.let { return it.groupValues[1].toIntOrNull() ?: 0 }
        return 0
    }

    private fun qualityLabel(q: Int): String = when (q) {
        2160 -> "4K"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "480p"; else -> "SD"
    }

    private fun parseQuality(text: String): Int = when {
        text.contains("2160") || text.contains("4k") -> 2160
        text.contains("1080") -> 1080
        text.contains("720") -> 720
        text.contains("480") -> 480
        else -> 0
    }

    private fun qualityToInt(q: String): Int = q.filter { it.isDigit() }.toIntOrNull() ?: 1080

    /** Pulls a "5.6 GB" / "720 MB" style size out of the release text, if present. */
    private fun parseSize(text: String): String? {
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(gb|mb|tb)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val num = m.groupValues[1].replace(',', '.')
        val unit = m.groupValues[2].uppercase(Locale.ROOT)
        return "$num $unit"
    }

    /** Detects the file container (MKV/MP4/…) from the release text, if present. */
    private fun parseContainer(text: String): String? {
        val m = Regex("\\b(mkv|mp4|avi|m2ts|ts|mov|wmv|webm)\\b", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        return m.groupValues[1].uppercase(Locale.ROOT)
    }

    /** Language hint for the picker. Torrentio rarely tags a single language, so
     *  we show "Multi" when multiple are flagged and "Original" otherwise. */
    private fun parseLanguage(text: String): String {
        val t = text.lowercase(Locale.ROOT)
        return if (Regex("multi|\\bdual\\b|vostfr").containsMatchIn(t)) "Multi" else "Original"
    }
}
