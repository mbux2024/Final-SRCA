package com.streambert.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.addons.AddonConfigServer
import com.streambert.tv.data.addons.AddonNetworkUtils
import com.streambert.tv.data.addons.AddonRepository
import com.streambert.tv.data.addons.InstalledAddon
import com.streambert.tv.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val tmdbKey: String = "",
    val torboxKey: String = "",
    val addonType: String = SettingsRepository.ADDON_TORRENTIO,
    val customAddonUrl: String = "",
    val realDebridKey: String = "",
    val scraperAddons: String = "",
    val autoplay: Boolean = true,
    val preferredQuality: String = "1080p",
    val tunnelingEnabled: Boolean = false,
    val omdbKey: String = "",
    val mdblistKey: String = "",
    val geminiKey: String = "",
    val badgeConfigUrl: String = "",
    val subtitleSize: String = "medium",
    val playerEngine: String = SettingsRepository.ENGINE_EXOPLAYER,
    val dvHandlingMode: String = SettingsRepository.DV_AUTO,
    val mpvHardwareDecoding: Boolean = true,
    val preferredAudioLang: String = "none",
    val preferredSubtitleLang: String = "none",
    val autoplayNextEnabled: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    // Nuvio-style stream feature filters (enum names)
    val streamDvFilter: String = "ANY",
    val streamHdrFilter: String = "ANY",
    val streamCodecFilter: String = "ANY",
    val streamMinQuality: String = "ANY",
    // TMDB metadata enrichment
    val tmdbEnrichEnabled: Boolean = true,
    val tmdbUseArtwork: Boolean = true,
    val tmdbUseBasicInfo: Boolean = true,
    val tmdbUseDetails: Boolean = true,
    val tmdbUseCredits: Boolean = true,
    val tmdbUseTrailers: Boolean = true,
    val tmdbUseMoreLikeThis: Boolean = true,
    val tmdbUseCollections: Boolean = true,
    // MDBList external rating providers
    val mdblistEnabled: Boolean = true,
    val mdbShowImdb: Boolean = true,
    val mdbShowTomatoes: Boolean = true,
    val mdbShowAudience: Boolean = true,
    val mdbShowMetacritic: Boolean = true,
    val mdbShowTmdb: Boolean = true,
    val mdbShowTrakt: Boolean = true,
    val mdbShowLetterboxd: Boolean = true,
    // Trakt.tv OAuth
    val traktClientId: String = "",
    val traktClientSecret: String = "",
    val traktConnected: Boolean = false,
    val traktUsername: String? = null,
    val traktConnecting: Boolean = false,
    val traktUserCode: String? = null,
    val traktVerificationUrl: String? = null,
    val traktStatus: String? = null,
    // Add-ons manager
    val installedAddons: List<InstalledAddon> = emptyList(),
    val addonsLoading: Boolean = false,
    val phoneManageUrl: String? = null,
    val loaded: Boolean = false,
    val saved: Boolean = false
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val addons: AddonRepository,
    private val trakt: com.streambert.tv.data.trakt.TraktAuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val qualityOptions = listOf("2160p", "1080p", "720p", "480p")

    init {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                tmdbKey = settings.currentTmdbKey(),
                torboxKey = settings.currentTorboxKey(),
                addonType = settings.currentAddonType(),
                customAddonUrl = settings.currentCustomAddonUrl(),
                realDebridKey = settings.currentRealDebridKey(),
                scraperAddons = settings.currentScraperAddons(),
                autoplay = settings.currentAutoplay(),
                preferredQuality = settings.currentPreferredQuality(),
                tunnelingEnabled = settings.currentTunnelingEnabled(),
                omdbKey = settings.currentOmdbKey(),
                mdblistKey = settings.currentMdblistKey(),
                geminiKey = settings.currentGeminiKey(),
                badgeConfigUrl = settings.currentBadgeConfigUrl(),
                subtitleSize = settings.currentSubtitleSize(),
                playerEngine = settings.currentPlayerEngine(),
                dvHandlingMode = settings.currentDvHandlingMode(),
                mpvHardwareDecoding = settings.currentMpvHardwareDecoding(),
                preferredAudioLang = settings.currentPreferredAudioLanguage(),
                preferredSubtitleLang = settings.currentPreferredSubtitleLanguage(),
                autoplayNextEnabled = settings.currentAutoplayNextEnabled(),
                skipIntroEnabled = settings.currentSkipIntroEnabled(),
                streamDvFilter = settings.currentStreamDolbyVisionFilter(),
                streamHdrFilter = settings.currentStreamHdrFilter(),
                streamCodecFilter = settings.currentStreamCodecFilter(),
                streamMinQuality = settings.currentStreamMinQuality(),
                tmdbEnrichEnabled = settings.currentTmdbEnrichEnabled(),
                tmdbUseArtwork = settings.currentTmdbUseArtwork(),
                tmdbUseBasicInfo = settings.currentTmdbUseBasicInfo(),
                tmdbUseDetails = settings.currentTmdbUseDetails(),
                tmdbUseCredits = settings.currentTmdbUseCredits(),
                tmdbUseTrailers = settings.currentTmdbUseTrailers(),
                tmdbUseMoreLikeThis = settings.currentTmdbUseMoreLikeThis(),
                tmdbUseCollections = settings.currentTmdbUseCollections(),
                mdblistEnabled = settings.currentMdblistEnabled(),
                mdbShowImdb = settings.currentMdbShowImdb(),
                mdbShowTomatoes = settings.currentMdbShowTomatoes(),
                mdbShowAudience = settings.currentMdbShowAudience(),
                mdbShowMetacritic = settings.currentMdbShowMetacritic(),
                mdbShowTmdb = settings.currentMdbShowTmdb(),
                mdbShowTrakt = settings.currentMdbShowTrakt(),
                mdbShowLetterboxd = settings.currentMdbShowLetterboxd(),
                traktClientId = settings.currentTraktClientId(),
                traktClientSecret = settings.currentTraktClientSecret(),
                loaded = true
            )
        }
        refreshInstalledAddons()
        observeTraktAuth()
    }

    // ── Trakt.tv OAuth ────────────────────────────────────────────────────
    private var traktPollJob: kotlinx.coroutines.Job? = null

    private fun observeTraktAuth() {
        viewModelScope.launch {
            trakt.authState.collect { st ->
                _state.value = _state.value.copy(
                    traktConnected = st.isAuthenticated,
                    traktUsername = st.username
                )
            }
        }
    }

    fun onTraktClientIdChange(v: String) { _state.value = _state.value.copy(traktClientId = v, saved = false) }
    fun onTraktClientSecretChange(v: String) { _state.value = _state.value.copy(traktClientSecret = v, saved = false) }

    /** Persist credentials, request a device code, then poll until approved. */
    fun connectTrakt() {
        if (_state.value.traktConnecting) return
        traktPollJob?.cancel()
        traktPollJob = viewModelScope.launch {
            // Persist credentials up-front so the API interceptor + auth repo use them.
            settings.setTraktClientId(_state.value.traktClientId.trim())
            settings.setTraktClientSecret(_state.value.traktClientSecret.trim())

            _state.value = _state.value.copy(
                traktConnecting = true, traktStatus = "Requesting code…",
                traktUserCode = null, traktVerificationUrl = null
            )

            val codeResult = trakt.startDeviceAuth()
            val code = codeResult.getOrElse { err ->
                _state.value = _state.value.copy(
                    traktConnecting = false,
                    traktStatus = err.message ?: "Couldn't start Trakt sign-in."
                )
                return@launch
            }

            _state.value = _state.value.copy(
                traktUserCode = code.userCode,
                traktVerificationUrl = code.verificationUrl,
                traktStatus = "Go to ${code.verificationUrl} and enter the code."
            )

            var intervalSec = code.interval.coerceAtLeast(1)
            val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
            while (System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(intervalSec * 1000L)
                when (val result = trakt.pollDeviceToken(code.deviceCode)) {
                    is com.streambert.tv.data.trakt.TraktPollResult.Approved -> {
                        _state.value = _state.value.copy(
                            traktConnecting = false, traktUserCode = null,
                            traktVerificationUrl = null,
                            traktStatus = "Connected as ${result.username ?: "your account"}."
                        )
                        return@launch
                    }
                    is com.streambert.tv.data.trakt.TraktPollResult.SlowDown ->
                        intervalSec = result.newInterval
                    com.streambert.tv.data.trakt.TraktPollResult.Pending -> { /* keep polling */ }
                    com.streambert.tv.data.trakt.TraktPollResult.AlreadyUsed -> {
                        _state.value = _state.value.copy(traktConnecting = false, traktStatus = "Code already used.")
                        return@launch
                    }
                    com.streambert.tv.data.trakt.TraktPollResult.Expired -> {
                        _state.value = _state.value.copy(
                            traktConnecting = false, traktUserCode = null, traktVerificationUrl = null,
                            traktStatus = "Code expired. Try connecting again."
                        )
                        return@launch
                    }
                    com.streambert.tv.data.trakt.TraktPollResult.Denied -> {
                        _state.value = _state.value.copy(
                            traktConnecting = false, traktUserCode = null, traktVerificationUrl = null,
                            traktStatus = "Sign-in was denied."
                        )
                        return@launch
                    }
                    is com.streambert.tv.data.trakt.TraktPollResult.Failed -> {
                        _state.value = _state.value.copy(traktConnecting = false, traktStatus = result.reason)
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(
                traktConnecting = false, traktUserCode = null, traktVerificationUrl = null,
                traktStatus = "Code expired. Try connecting again."
            )
        }
    }

    fun cancelTraktConnect() {
        traktPollJob?.cancel()
        _state.value = _state.value.copy(
            traktConnecting = false, traktUserCode = null, traktVerificationUrl = null, traktStatus = null
        )
    }

    fun disconnectTrakt() {
        viewModelScope.launch {
            trakt.signOut()
            _state.value = _state.value.copy(
                traktConnected = false, traktUsername = null, traktStatus = null,
                traktUserCode = null, traktVerificationUrl = null, traktConnecting = false
            )
        }
    }

    fun onTmdbChange(v: String) { _state.value = _state.value.copy(tmdbKey = v, saved = false) }
    fun onTorboxChange(v: String) { _state.value = _state.value.copy(torboxKey = v, saved = false) }
    fun onAddonTypeChange(v: String) { _state.value = _state.value.copy(addonType = v, saved = false) }
    fun onCustomAddonUrlChange(v: String) { _state.value = _state.value.copy(customAddonUrl = v, saved = false) }
    fun onRealDebridKeyChange(v: String) { _state.value = _state.value.copy(realDebridKey = v, saved = false) }

    /** Connect/replace the TorBox key from the tap dialog (persists immediately). */
    fun connectTorbox(key: String) {
        val v = key.trim()
        _state.value = _state.value.copy(torboxKey = v, saved = false)
        viewModelScope.launch { settings.setTorboxKey(v) }
    }

    /** Connect/replace the Real-Debrid key from the tap dialog (persists immediately). */
    fun connectRealDebrid(key: String) {
        val v = key.trim()
        _state.value = _state.value.copy(realDebridKey = v, saved = false)
        viewModelScope.launch { settings.setRealDebridKey(v) }
    }
    fun onScraperAddonsChange(v: String) { _state.value = _state.value.copy(scraperAddons = v, saved = false) }
    fun onAutoplayChange(v: Boolean) { _state.value = _state.value.copy(autoplay = v, saved = false) }
    fun onQualityChange(v: String) { _state.value = _state.value.copy(preferredQuality = v, saved = false) }
    fun onTunnelingChange(v: Boolean) { _state.value = _state.value.copy(tunnelingEnabled = v, saved = false) }
    fun onOmdbKeyChange(v: String) { _state.value = _state.value.copy(omdbKey = v, saved = false) }
    fun onMdblistKeyChange(v: String) { _state.value = _state.value.copy(mdblistKey = v, saved = false) }
    fun onGeminiKeyChange(v: String) { _state.value = _state.value.copy(geminiKey = v, saved = false) }
    fun onBadgeConfigUrlChange(v: String) { _state.value = _state.value.copy(badgeConfigUrl = v, saved = false) }
    fun onSubtitleSizeChange(v: String) { _state.value = _state.value.copy(subtitleSize = v, saved = false) }
    fun onPlayerEngineChange(v: String) { _state.value = _state.value.copy(playerEngine = v, saved = false) }

    /** Nuvio-style engine option metadata for the "Internal Engine" picker. */
    data class EngineOption(val id: String, val title: String, val description: String)
    val engineOptions = listOf(
        EngineOption(SettingsRepository.ENGINE_EXOPLAYER, "ExoPlayer", "Best compatibility with current app features."),
        EngineOption(SettingsRepository.ENGINE_MPV, "Libmpv (Beta)", "Uses libmpv with our OSD controls. Experimental."),
        EngineOption(SettingsRepository.ENGINE_AUTO, "Auto (Best for Content)", "ExoPlayer for Movies & TV Shows · MPV for Anime")
    )
    fun playerEngineLabel(engine: String): String =
        engineOptions.firstOrNull { it.id == engine }?.title ?: "ExoPlayer"

    /** Nuvio-style Dolby Vision (DV7) handling options for the picker. */
    fun onDvHandlingModeChange(v: String) { _state.value = _state.value.copy(dvHandlingMode = v, saved = false) }
    val dvHandlingOptions = listOf(
        EngineOption(SettingsRepository.DV_AUTO, "Auto (Recommended)", "Query display capabilities and let the decoder handle DV."),
        EngineOption(SettingsRepository.DV_HDR10_BASE_LAYER, "HDR10 Base Layer", "Ignore DV metadata and play the HEVC/HDR10 base layer."),
        EngineOption(SettingsRepository.DV_DV81_LIBDOVI, "Convert to DV8.1", "libdovi DV7 → DV8.1 conversion for wider TV support."),
        EngineOption(SettingsRepository.DV_STRIP, "Strip Dolby Vision", "Remove DV metadata (libdovi) and play as HDR/SDR."),
        EngineOption(SettingsRepository.DV_OFF, "Off (Passthrough)", "Pass DV7 through untouched (may glitch on some hardware).")
    )
    fun dvHandlingLabel(mode: String): String =
        dvHandlingOptions.firstOrNull { it.id == mode }?.title ?: "Auto (Recommended)"

    val languageOptions = listOf("none", "en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh", "ar", "hi")
    fun languageLabel(code: String): String =
        if (code == "none") "Off" else com.streambert.tv.data.stream.LanguageNames.displayName(code)

    fun onHardwareDecodingChange(v: Boolean) { _state.value = _state.value.copy(mpvHardwareDecoding = v, saved = false) }
    fun cyclePreferredAudioLang() {
        val i = languageOptions.indexOf(_state.value.preferredAudioLang).coerceAtLeast(0)
        val next = languageOptions[(i + 1) % languageOptions.size]
        _state.value = _state.value.copy(preferredAudioLang = next, saved = false)
    }
    fun cyclePreferredSubtitleLang() {
        val i = languageOptions.indexOf(_state.value.preferredSubtitleLang).coerceAtLeast(0)
        val next = languageOptions[(i + 1) % languageOptions.size]
        _state.value = _state.value.copy(preferredSubtitleLang = next, saved = false)
    }
    fun onAutoplayNextChange(v: Boolean) { _state.value = _state.value.copy(autoplayNextEnabled = v, saved = false) }
    fun onSkipIntroChange(v: Boolean) { _state.value = _state.value.copy(skipIntroEnabled = v, saved = false) }

    // ── Nuvio-style stream feature filters ────────────────────────────────
    // Tri-state DV/HDR filters, single-codec filter, minimum-quality floor.
    val dynamicRangeFilterOptions = listOf("ANY", "EXCLUDE", "ONLY")
    val codecFilterOptions = listOf("ANY", "H264", "HEVC", "AV1")
    val minQualityOptions = listOf("ANY", "P720", "P1080", "P2160")

    fun dynamicRangeFilterLabel(v: String): String = when (v) {
        "EXCLUDE" -> "Exclude"
        "ONLY" -> "Only"
        else -> "Any"
    }
    fun codecFilterLabel(v: String): String = when (v) {
        "H264" -> "H.264"; "HEVC" -> "HEVC"; "AV1" -> "AV1"; else -> "Any"
    }
    fun minQualityLabel(v: String): String = when (v) {
        "P720" -> "720p+"; "P1080" -> "1080p+"; "P2160" -> "4K only"; else -> "Any"
    }

    private fun cycle(options: List<String>, current: String): String {
        val i = options.indexOf(current).coerceAtLeast(0)
        return options[(i + 1) % options.size]
    }
    fun cycleStreamDvFilter() {
        _state.value = _state.value.copy(streamDvFilter = cycle(dynamicRangeFilterOptions, _state.value.streamDvFilter), saved = false)
    }
    fun cycleStreamHdrFilter() {
        _state.value = _state.value.copy(streamHdrFilter = cycle(dynamicRangeFilterOptions, _state.value.streamHdrFilter), saved = false)
    }
    fun cycleStreamCodecFilter() {
        _state.value = _state.value.copy(streamCodecFilter = cycle(codecFilterOptions, _state.value.streamCodecFilter), saved = false)
    }
    fun cycleStreamMinQuality() {
        _state.value = _state.value.copy(streamMinQuality = cycle(minQualityOptions, _state.value.streamMinQuality), saved = false)
    }

    val subtitleSizes = listOf("small", "medium", "large")

    // ── TMDB enrichment toggles ───────────────────────────────────────────
    fun onTmdbEnrichEnabledChange(v: Boolean) { _state.value = _state.value.copy(tmdbEnrichEnabled = v, saved = false) }
    fun onTmdbUseArtworkChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseArtwork = v, saved = false) }
    fun onTmdbUseBasicInfoChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseBasicInfo = v, saved = false) }
    fun onTmdbUseDetailsChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseDetails = v, saved = false) }
    fun onTmdbUseCreditsChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseCredits = v, saved = false) }
    fun onTmdbUseTrailersChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseTrailers = v, saved = false) }
    fun onTmdbUseMoreLikeThisChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseMoreLikeThis = v, saved = false) }
    fun onTmdbUseCollectionsChange(v: Boolean) { _state.value = _state.value.copy(tmdbUseCollections = v, saved = false) }

    // ── MDBList provider toggles ──────────────────────────────────────────
    fun onMdblistEnabledChange(v: Boolean) { _state.value = _state.value.copy(mdblistEnabled = v, saved = false) }
    fun onMdbShowImdbChange(v: Boolean) { _state.value = _state.value.copy(mdbShowImdb = v, saved = false) }
    fun onMdbShowTomatoesChange(v: Boolean) { _state.value = _state.value.copy(mdbShowTomatoes = v, saved = false) }
    fun onMdbShowAudienceChange(v: Boolean) { _state.value = _state.value.copy(mdbShowAudience = v, saved = false) }
    fun onMdbShowMetacriticChange(v: Boolean) { _state.value = _state.value.copy(mdbShowMetacritic = v, saved = false) }
    fun onMdbShowTmdbChange(v: Boolean) { _state.value = _state.value.copy(mdbShowTmdb = v, saved = false) }
    fun onMdbShowTraktChange(v: Boolean) { _state.value = _state.value.copy(mdbShowTrakt = v, saved = false) }
    fun onMdbShowLetterboxdChange(v: Boolean) { _state.value = _state.value.copy(mdbShowLetterboxd = v, saved = false) }

    // ── Add-ons management (scraper stream add-ons, one manifest URL per line) ──
    fun addonLines(): List<String> =
        _state.value.scraperAddons.lineSequence().map { it.trim() }.filter { it.startsWith("http") }.toList()

    private fun persistAddons(lines: List<String>) {
        val joined = lines.joinToString("\n")
        _state.value = _state.value.copy(scraperAddons = joined, saved = false)
        viewModelScope.launch { settings.setScraperAddons(joined) }
    }

    fun addAddon(url: String) {
        val u = url.trim()
        if (!u.startsWith("http")) return
        val lines = addonLines().toMutableList()
        if (lines.none { it.equals(u, ignoreCase = true) }) { lines.add(u); persistAddons(lines) }
    }

    fun removeAddon(index: Int) {
        val lines = addonLines().toMutableList()
        if (index in lines.indices) { lines.removeAt(index); persistAddons(lines) }
    }

    fun moveAddon(index: Int, up: Boolean) {
        val lines = addonLines().toMutableList()
        val target = if (up) index - 1 else index + 1
        if (index in lines.indices && target in lines.indices) {
            val tmp = lines[index]; lines[index] = lines[target]; lines[target] = tmp
            persistAddons(lines)
        }
    }

    // ── Add-ons manager (Stremio addons + QR phone-management) ────────────
    @Volatile private var latestStreamUrls: List<String> = emptyList()
    private var configServer: AddonConfigServer? = null

    fun refreshInstalledAddons() {
        _state.value = _state.value.copy(addonsLoading = true)
        viewModelScope.launch {
            val list = addons.loadInstalled()
            latestStreamUrls = list.filter { it.kind == com.streambert.tv.data.addons.AddonKind.STREAM }.map { it.baseUrl }
            _state.value = _state.value.copy(installedAddons = list, addonsLoading = false)
        }
    }

    fun installStreamAddon(url: String) {
        viewModelScope.launch { addons.addStreamAddon(url); refreshInstalledAddons() }
    }

    fun uninstallStreamAddon(url: String) {
        viewModelScope.launch { addons.removeStreamAddon(url); refreshInstalledAddons() }
    }

    fun reorderStreamAddon(index: Int, up: Boolean) {
        viewModelScope.launch { addons.moveStreamAddon(index, up); refreshInstalledAddons() }
    }

    /** Start the on-device web server + expose its LAN URL for the QR code. */
    fun startPhoneManagement() {
        if (configServer != null) return
        val server = AddonConfigServer.startOnAvailablePort(
            currentAddons = { latestStreamUrls },
            onAdd = { url -> viewModelScope.launch { addons.addStreamAddon(url); refreshInstalledAddons() } },
            onRemove = { url -> viewModelScope.launch { addons.removeStreamAddon(url); refreshInstalledAddons() } },
            onSetOrder = { urls -> viewModelScope.launch { addons.setStreamAddons(urls); refreshInstalledAddons() } }
        )
        configServer = server
        val ip = AddonNetworkUtils.localIpAddress()
        _state.value = _state.value.copy(
            phoneManageUrl = if (server != null && ip != null) "http://$ip:${server.listeningPort}/" else null
        )
    }

    fun stopPhoneManagement() {
        runCatching { configServer?.stop() }
        configServer = null
        _state.value = _state.value.copy(phoneManageUrl = null)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { configServer?.stop() }
        configServer = null
        traktPollJob?.cancel()
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            settings.setTmdbKey(s.tmdbKey)
            settings.setTorboxKey(s.torboxKey)
            settings.setAddonType(s.addonType)
            settings.setCustomAddonUrl(s.customAddonUrl)
            settings.setRealDebridKey(s.realDebridKey)
            settings.setScraperAddons(s.scraperAddons)
            settings.setAutoplay(s.autoplay)
            settings.setPreferredQuality(s.preferredQuality)
            settings.setTunnelingEnabled(s.tunnelingEnabled)
            settings.setOmdbKey(s.omdbKey)
            settings.setMdblistKey(s.mdblistKey)
            settings.setGeminiKey(s.geminiKey)
            settings.setBadgeConfigUrl(s.badgeConfigUrl)
            settings.setSubtitleSize(s.subtitleSize)
            settings.setPlayerEngine(s.playerEngine)
            settings.setDvHandlingMode(s.dvHandlingMode)
            settings.setMpvHardwareDecoding(s.mpvHardwareDecoding)
            settings.setPreferredAudioLanguage(s.preferredAudioLang)
            settings.setPreferredSubtitleLanguage(s.preferredSubtitleLang)
            settings.setAutoplayNextEnabled(s.autoplayNextEnabled)
            settings.setSkipIntroEnabled(s.skipIntroEnabled)
            settings.setStreamDolbyVisionFilter(s.streamDvFilter)
            settings.setStreamHdrFilter(s.streamHdrFilter)
            settings.setStreamCodecFilter(s.streamCodecFilter)
            settings.setStreamMinQuality(s.streamMinQuality)
            settings.setTmdbEnrichEnabled(s.tmdbEnrichEnabled)
            settings.setTmdbUseArtwork(s.tmdbUseArtwork)
            settings.setTmdbUseBasicInfo(s.tmdbUseBasicInfo)
            settings.setTmdbUseDetails(s.tmdbUseDetails)
            settings.setTmdbUseCredits(s.tmdbUseCredits)
            settings.setTmdbUseTrailers(s.tmdbUseTrailers)
            settings.setTmdbUseMoreLikeThis(s.tmdbUseMoreLikeThis)
            settings.setTmdbUseCollections(s.tmdbUseCollections)
            settings.setMdblistEnabled(s.mdblistEnabled)
            settings.setMdbShowImdb(s.mdbShowImdb)
            settings.setMdbShowTomatoes(s.mdbShowTomatoes)
            settings.setMdbShowAudience(s.mdbShowAudience)
            settings.setMdbShowMetacritic(s.mdbShowMetacritic)
            settings.setMdbShowTmdb(s.mdbShowTmdb)
            settings.setMdbShowTrakt(s.mdbShowTrakt)
            settings.setMdbShowLetterboxd(s.mdbShowLetterboxd)
            settings.setTraktClientId(s.traktClientId)
            settings.setTraktClientSecret(s.traktClientSecret)
            _state.value = _state.value.copy(saved = true)
        }
    }
}
