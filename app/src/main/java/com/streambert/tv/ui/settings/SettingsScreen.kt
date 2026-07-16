package com.streambert.tv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.streambert.tv.data.addons.AddonNetworkUtils
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.ui.components.TvTextField
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

private const val PROVIDER_TORBOX = "torbox"
private const val PROVIDER_REALDEBRID = "realdebrid"

private val RailBg = Color(0xFF0A0A0E)
private val PillIdle = Color(0xFF17171E)
private val PillSelected = Color(0xFF2A2A32)
private val CardBg = Color(0xFF16161C)

/** NuvioTV-style settings categories: icon + title + one-line subtitle. */
private enum class SettingsCategory(
    val title: String,      // short label shown in the left rail
    val header: String,     // big title shown in the content pane
    val subtitle: String,   // one-line subtitle under the header
    val icon: ImageVector
) {
    APPEARANCE("Appearance", "Appearance", "Personalize the look and feel.", Icons.Filled.Palette),
    LAYOUT("Layout", "Layout", "Home rows, cards and density.", Icons.Filled.GridView),
    CONTENT("Content & Discovery", "Content & Discovery", "Add-ons and catalog sources.", Icons.Filled.Explore),
    INTEGRATIONS("Integrations", "Integrations", "Manage available integrations", Icons.Filled.Link),
    PLAYBACK("Playback", "Playback Settings", "Configure video playback and subtitle options", Icons.Rounded.PlayArrow),
    TRAKT("Trakt", "Trakt", "Sync your watch history and watchlist.", Icons.Filled.Sync),
    ABOUT("About", "About", "App version and information.", Icons.Filled.Info),
    ADVANCED("Advanced", "Advanced", "Diagnostics and data management.", Icons.Filled.Build)
}


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var category by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var activeDebridDialog by remember { mutableStateOf<String?>(null) }
    var showAddAddon by remember { mutableStateOf(false) }
    var showEnginePicker by remember { mutableStateOf(false) }
    var showDvPicker by remember { mutableStateOf(false) }

    // Accordion helper: only one section open at a time (reset when category changes).
    val onToggle: (String) -> Unit = { key -> expanded = if (expanded == key) null else key }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxSize()) {
            // ── Left category rail (pill items with icons) ──────────────
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(RailBg)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingsCategory.values().forEach { c ->
                    CategoryRailItem(
                        icon = c.icon,
                        title = c.title,
                        selected = category == c,
                        onClick = { category = c; expanded = null }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.colors(containerColor = PillIdle),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Back", modifier = Modifier.padding(vertical = 4.dp)) }
            }

            // ── Right content pane ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 44.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header: big title + subtitle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            category.header,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            category.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.saved) {
                        Text(
                            "Saved",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                    Button(onClick = { viewModel.save() }) {
                        Text("Save", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))

                when (category) {
                    SettingsCategory.APPEARANCE -> AppearanceContent(expanded, onToggle)
                    SettingsCategory.LAYOUT -> LayoutContent(expanded, onToggle)
                    SettingsCategory.CONTENT -> ContentDiscoveryContent(state, viewModel, expanded, onToggle) { showAddAddon = true }
                    SettingsCategory.INTEGRATIONS -> IntegrationsContent(state, viewModel, expanded, onToggle) { activeDebridDialog = it }
                    SettingsCategory.PLAYBACK -> PlaybackContent(
                        state, viewModel, expanded, onToggle,
                        onPickEngine = { showEnginePicker = true },
                        onPickDv = { showDvPicker = true }
                    )
                    SettingsCategory.TRAKT -> TraktContent(state, viewModel, expanded, onToggle)
                    SettingsCategory.ABOUT -> AboutContent(expanded, onToggle)
                    SettingsCategory.ADVANCED -> AdvancedContent(state, viewModel, expanded, onToggle)
                }
            }
        }

        // Debrid connect dialog
        activeDebridDialog?.let { provider ->
            val isTorbox = provider == PROVIDER_TORBOX
            DebridKeyDialog(
                providerName = if (isTorbox) "TorBox" else "Real-Debrid",
                currentValue = if (isTorbox) state.torboxKey else state.realDebridKey,
                onSave = { key ->
                    if (isTorbox) viewModel.connectTorbox(key) else viewModel.connectRealDebrid(key)
                    activeDebridDialog = null
                },
                onClear = {
                    if (isTorbox) viewModel.connectTorbox("") else viewModel.connectRealDebrid("")
                    activeDebridDialog = null
                },
                onDismiss = { activeDebridDialog = null }
            )
        }
        if (showAddAddon) {
            AddAddonDialog(
                onSave = { url -> viewModel.installStreamAddon(url); showAddAddon = false },
                onDismiss = { showAddAddon = false }
            )
        }
        state.phoneManageUrl?.let { url ->
            QrManageDialog(url = url, onDismiss = { viewModel.stopPhoneManagement() })
        }
        if (showEnginePicker) {
            OptionPickerDialog(
                title = "Internal Engine",
                options = viewModel.engineOptions,
                selected = state.playerEngine,
                onSelect = { viewModel.onPlayerEngineChange(it); showEnginePicker = false },
                onDismiss = { showEnginePicker = false }
            )
        }
        if (showDvPicker) {
            OptionPickerDialog(
                title = "Dolby Vision Handling",
                options = viewModel.dvHandlingOptions,
                selected = state.dvHandlingMode,
                onSelect = { viewModel.onDvHandlingModeChange(it); showDvPicker = false },
                onDismiss = { showDvPicker = false }
            )
        }
    }
}


// ══════════════════════════ PLAYBACK ══════════════════════════

@Composable
private fun PlaybackContent(
    state: SettingsUiState,
    vm: SettingsViewModel,
    expanded: String?,
    onToggle: (String) -> Unit,
    onPickEngine: () -> Unit,
    onPickDv: () -> Unit
) {
    ExpandableSection("General", "Core playback behavior.", expanded == "pb_general", { onToggle("pb_general") }) {
        ToggleRow("Auto-play on select", "Start playback immediately via your debrid service.", state.autoplay) { vm.onAutoplayChange(it) }
        ToggleRow("Auto-play next episode", "As the credits start, show an 'Up next' countdown and roll into the next episode.", state.autoplayNextEnabled) { vm.onAutoplayNextChange(it) }
        ToggleRow("Skip intro button", "Show a 'Skip Intro' button early in TV episodes.", state.skipIntroEnabled) { vm.onSkipIntroChange(it) }
    }

    ExpandableSection("Player & Stream Selection", "Player preference, auto-play, and source filtering.", expanded == "pb_player", { onToggle("pb_player") }) {
        ChoiceRow(
            "Internal Engine",
            "ExoPlayer (best compatibility), Libmpv (beta, more formats) or Auto.",
            vm.playerEngineLabel(state.playerEngine)
        ) { onPickEngine() }
        OptionsRow("Preferred quality", vm.qualityOptions.map { it to it }, state.preferredQuality) { vm.onQualityChange(it) }
        SectionLabel("Stream filters")
        ChoiceRow("Dolby Vision", "Any = no filter · Exclude = hide DV releases · Only = DV releases only.", vm.dynamicRangeFilterLabel(state.streamDvFilter)) { vm.cycleStreamDvFilter() }
        ChoiceRow("HDR", "Any / Exclude / Only for HDR, HDR10, HDR10+, HLG (and DV).", vm.dynamicRangeFilterLabel(state.streamHdrFilter)) { vm.cycleStreamHdrFilter() }
        ChoiceRow("Video codec", "Restrict sources to a single codec. HEVC/AV1 are smaller; H.264 is the most widely supported.", vm.codecFilterLabel(state.streamCodecFilter)) { vm.cycleStreamCodecFilter() }
        ChoiceRow("Minimum quality", "Drop sources below this resolution.", vm.minQualityLabel(state.streamMinQuality)) { vm.cycleStreamMinQuality() }
    }

    ExpandableSection("Audio & Video", "Audio controls and video compatibility.", expanded == "pb_av", { onToggle("pb_av") }) {
        ChoiceRow("Preferred audio language", "Auto-selects this audio track when available.", vm.languageLabel(state.preferredAudioLang)) { vm.cyclePreferredAudioLang() }
        ChoiceRow("Dolby Vision handling", "How DV7 streams are handled. Auto is recommended.", vm.dvHandlingLabel(state.dvHandlingMode)) { onPickDv() }
        ToggleRow("MPV hardware decoding", "Turn OFF to force software decoding if video is garbled.", state.mpvHardwareDecoding) { vm.onHardwareDecodingChange(it) }
        ToggleRow("Tunneled playback (advanced)", "Leave OFF if video is a black screen (audio only).", state.tunnelingEnabled) { vm.onTunnelingChange(it) }
    }

    ExpandableSection("Subtitles", "Language, style, and render mode.", expanded == "pb_subs", { onToggle("pb_subs") }) {
        ChoiceRow("Preferred subtitle language", "Auto-enables a matching subtitle when available.", vm.languageLabel(state.preferredSubtitleLang)) { vm.cyclePreferredSubtitleLang() }
        OptionsRow("Subtitle size", vm.subtitleSizes.map { it to it.replaceFirstChar { c -> c.uppercase() } }, state.subtitleSize) { vm.onSubtitleSizeChange(it) }
    }

    ExpandableSection("P2P Streaming", "Allow peer-to-peer (torrent) streams.", expanded == "pb_p2p", { onToggle("pb_p2p") }) {
        InfoText("Torrent sources are resolved instantly through your connected debrid service (TorBox / Real-Debrid) rather than streamed peer-to-peer on-device. Connect a debrid service under Integrations to enable instant playback.")
    }
}


// ══════════════════════════ CONTENT & DISCOVERY ══════════════════════════

@Composable
private fun ContentDiscoveryContent(
    state: SettingsUiState,
    vm: SettingsViewModel,
    expanded: String?,
    onToggle: (String) -> Unit,
    onAddAddon: () -> Unit
) {
    ExpandableSection("Add-ons", "Stream & subtitle add-ons (order = priority).", expanded == "cd_addons", { onToggle("cd_addons") }) {
        SectionLabel("Stream source")
        OptionsRow(
            "",
            listOf(
                SettingsRepository.ADDON_TORRENTIO to "Torrentio + debrid",
                SettingsRepository.ADDON_CUSTOM to "Custom (Comet) URL"
            ),
            state.addonType
        ) { vm.onAddonTypeChange(it) }
        if (state.addonType == SettingsRepository.ADDON_CUSTOM) {
            TvTextField(
                value = state.customAddonUrl,
                onValueChange = vm::onCustomAddonUrlChange,
                label = "Custom addon URL (…/manifest.json)",
                modifier = Modifier.width(640.dp),
                imeAction = ImeAction.Done
            )
        }
        SectionLabel("Source badges")
        InfoText("URL of a NuvioTV-format badge config (JSON). Badge images are matched against each source's release title. Use a raw-friendly host (e.g. GitHub raw / gist) if the link is blocked.")
        TvTextField(
            value = state.badgeConfigUrl,
            onValueChange = vm::onBadgeConfigUrlChange,
            label = "Badge config URL",
            modifier = Modifier.width(640.dp),
            imeAction = ImeAction.Done
        )
        SectionLabel("Installed add-ons")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAddAddon) { Text("Add add-on", modifier = Modifier.padding(horizontal = 14.dp)) }
            Button(onClick = { vm.refreshInstalledAddons() }, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(if (state.addonsLoading) "Refreshing…" else "Refresh", modifier = Modifier.padding(horizontal = 14.dp))
            }
            Button(onClick = { vm.startPhoneManagement() }, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("Manage on phone (QR)", modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
        state.installedAddons.forEachIndexed { index, addon ->
            InstalledAddonRow(
                index = index,
                name = addon.displayName,
                version = addon.version,
                summary = addon.typeSummary,
                url = addon.baseUrl,
                onUp = { vm.reorderStreamAddon(index, up = true) },
                onDown = { vm.reorderStreamAddon(index, up = false) },
                onRemove = { vm.uninstallStreamAddon(addon.baseUrl) }
            )
        }
        if (state.installedAddons.isEmpty() && !state.addonsLoading) {
            InfoText("No add-ons installed.")
        }
    }
}

// ══════════════════════════ INTEGRATIONS ══════════════════════════

@Composable
private fun IntegrationsContent(
    state: SettingsUiState,
    vm: SettingsViewModel,
    expanded: String?,
    onToggle: (String) -> Unit,
    onConnect: (String) -> Unit
) {
    ExpandableSection("Connected Services", "Experimental cloud account sources.", expanded == "in_connected", { onToggle("in_connected") }) {
        DebridRow("TorBox", "Instant cached streams + scraper resolving.", connectionStatus(state.torboxKey), state.torboxKey.isNotBlank()) { onConnect(PROVIDER_TORBOX) }
        DebridRow("Real-Debrid", "Resolves the same scraper sources via Real-Debrid.", connectionStatus(state.realDebridKey), state.realDebridKey.isNotBlank()) { onConnect(PROVIDER_REALDEBRID) }
    }
    ExpandableSection("TMDB", "Metadata enrichment controls.", expanded == "in_tmdb", { onToggle("in_tmdb") }) {
        TvTextField(state.tmdbKey, vm::onTmdbChange, "TMDB Read Access Token (v4)", modifier = Modifier.width(640.dp), imeAction = ImeAction.Done)
        InfoText("A read-access token is bundled so the app works out of the box. Paste your own to override it.")
        SectionLabel("Enrichment")
        ToggleRow("Enable TMDB enrichment", "Master switch for the metadata below.", state.tmdbEnrichEnabled) { vm.onTmdbEnrichEnabledChange(it) }
        ToggleRow("Artwork (posters, backdrops)", null, state.tmdbUseArtwork) { vm.onTmdbUseArtworkChange(it) }
        ToggleRow("Basic info (overview, genres, rating)", null, state.tmdbUseBasicInfo) { vm.onTmdbUseBasicInfoChange(it) }
        ToggleRow("Details (runtime, seasons)", null, state.tmdbUseDetails) { vm.onTmdbUseDetailsChange(it) }
        ToggleRow("Cast & crew", null, state.tmdbUseCredits) { vm.onTmdbUseCreditsChange(it) }
        ToggleRow("Trailers", "Show the Trailer button on detail pages.", state.tmdbUseTrailers) { vm.onTmdbUseTrailersChange(it) }
        ToggleRow("More like this (recommendations)", null, state.tmdbUseMoreLikeThis) { vm.onTmdbUseMoreLikeThisChange(it) }
        ToggleRow("Collections", null, state.tmdbUseCollections) { vm.onTmdbUseCollectionsChange(it) }
    }
    ExpandableSection("MDBList", "External ratings providers.", expanded == "in_mdb", { onToggle("in_mdb") }) {
        ToggleRow("Enable MDBList ratings", "Show multi-source ratings on detail pages.", state.mdblistEnabled) { vm.onMdblistEnabledChange(it) }
        TvTextField(state.mdblistKey, vm::onMdblistKeyChange, "MDBList API key", modifier = Modifier.width(640.dp), imeAction = ImeAction.Done)
        SectionLabel("Providers shown")
        ToggleRow("IMDb", null, state.mdbShowImdb) { vm.onMdbShowImdbChange(it) }
        ToggleRow("Rotten Tomatoes", null, state.mdbShowTomatoes) { vm.onMdbShowTomatoesChange(it) }
        ToggleRow("Audience", null, state.mdbShowAudience) { vm.onMdbShowAudienceChange(it) }
        ToggleRow("Metacritic", null, state.mdbShowMetacritic) { vm.onMdbShowMetacriticChange(it) }
        ToggleRow("TMDB", null, state.mdbShowTmdb) { vm.onMdbShowTmdbChange(it) }
        ToggleRow("Trakt", null, state.mdbShowTrakt) { vm.onMdbShowTraktChange(it) }
        ToggleRow("Letterboxd", null, state.mdbShowLetterboxd) { vm.onMdbShowLetterboxdChange(it) }
        SectionLabel("OMDb (fallback IMDb rating)")
        TvTextField(state.omdbKey, vm::onOmdbKeyChange, "OMDb API key", modifier = Modifier.width(640.dp), imeAction = ImeAction.Done)
        SectionLabel("Google Gemini (AI features)")
        TvTextField(state.geminiKey, vm::onGeminiKeyChange, "Google Gemini API key", modifier = Modifier.width(640.dp), imeAction = ImeAction.Done)
    }
}


// ══════════════════════════ APPEARANCE / LAYOUT / TRAKT / ABOUT / ADVANCED ══════════════════════════

@Composable
private fun AppearanceContent(expanded: String?, onToggle: (String) -> Unit) {
    ExpandableSection("Theme", "App colors and accent.", expanded == "ap_theme", { onToggle("ap_theme") }) {
        InfoText("Streambert uses a dark theme with a red accent, tuned for the 10-foot TV experience. Additional theme options are coming soon.")
    }
    ExpandableSection("Interface", "Text size and animations.", expanded == "ap_ui", { onToggle("ap_ui") }) {
        InfoText("Interface scaling and motion options will appear here.")
    }
}

@Composable
private fun LayoutContent(expanded: String?, onToggle: (String) -> Unit) {
    ExpandableSection("Home", "Rows shown on the home screen.", expanded == "ly_home", { onToggle("ly_home") }) {
        InfoText("Home layout options (Continue Watching, Trending, catalogs and their order) will appear here.")
    }
    ExpandableSection("Cards", "Poster style and density.", expanded == "ly_cards", { onToggle("ly_cards") }) {
        InfoText("Poster card style and grid density options will appear here.")
    }
}

@Composable
private fun TraktContent(
    state: SettingsUiState,
    vm: SettingsViewModel,
    expanded: String?,
    onToggle: (String) -> Unit
) {
    ExpandableSection("Account", "Connect your Trakt account.", expanded == "tk_account", { onToggle("tk_account") }) {
        if (state.traktConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF46D369), modifier = Modifier.size(22.dp))
                Text(
                    "Connected as ${state.traktUsername ?: "your account"}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.disconnectTrakt() },
                colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text("Disconnect Trakt", modifier = Modifier.padding(horizontal = 12.dp))
            }
        } else {
            InfoText(
                "Create a free API app at trakt.tv/oauth/applications (Redirect URI: urn:ietf:wg:oauth:2.0:oob), " +
                    "then paste its Client ID and Secret below and connect."
            )
            TvTextField(
                value = state.traktClientId,
                onValueChange = vm::onTraktClientIdChange,
                label = "Trakt Client ID",
                modifier = Modifier.width(640.dp)
            )
            Spacer(Modifier.height(8.dp))
            TvTextField(
                value = state.traktClientSecret,
                onValueChange = vm::onTraktClientSecretChange,
                label = "Trakt Client Secret",
                modifier = Modifier.width(640.dp),
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(12.dp))

            // Device-code prompt while a sign-in is in progress.
            if (state.traktUserCode != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBg)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "1. On your phone or computer, go to:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            state.traktVerificationUrl ?: "trakt.tv/activate",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "2. Enter this code:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            state.traktUserCode ?: "",
                            color = Color(0xFFE50914),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.connectTrakt() },
                    colors = ButtonDefaults.colors(containerColor = Color(0xFFE50914), contentColor = Color.White)
                ) {
                    Text(
                        if (state.traktConnecting) "Waiting for approval…" else "Connect Trakt",
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                if (state.traktConnecting) {
                    Button(
                        onClick = { vm.cancelTraktConnect() },
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text("Cancel", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
        state.traktStatus?.let {
            Spacer(Modifier.height(10.dp))
            InfoText(it)
        }
    }
    ExpandableSection("Watchlist", "Your Trakt watchlist on the home screen.", expanded == "tk_sync", { onToggle("tk_sync") }) {
        InfoText(
            if (state.traktConnected)
                "Your Trakt watchlist appears as a row on the Home tab. Add or remove titles on Trakt and they'll sync here."
            else
                "Connect your Trakt account above to show your watchlist as a row on the Home screen."
        )
    }
}

@Composable
private fun AboutContent(expanded: String?, onToggle: (String) -> Unit) {
    ExpandableSection("About Streambert TV", "Version and information.", expanded == "ab_about", { onToggle("ab_about") }) {
        Text("Streambert TV", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        InfoText("A native Android TV client with TMDB browsing, TorBox / Real-Debrid instant streaming, MPV playback, TMDB enrichment and MDBList ratings.")
        InfoText("Version 1.0.0")
    }
}

@Composable
private fun AdvancedContent(
    state: SettingsUiState,
    vm: SettingsViewModel,
    expanded: String?,
    onToggle: (String) -> Unit
) {
    ExpandableSection("Playback engine", "Low-level decoding options.", expanded == "adv_engine", { onToggle("adv_engine") }) {
        ToggleRow("MPV hardware decoding", "Turn OFF to force software decoding if video is garbled.", state.mpvHardwareDecoding) { vm.onHardwareDecodingChange(it) }
        ToggleRow("Tunneled playback", "Leave OFF if video is a black screen (audio only).", state.tunnelingEnabled) { vm.onTunnelingChange(it) }
    }
    ExpandableSection("Add-ons", "Manage installed add-ons.", expanded == "adv_addons", { onToggle("adv_addons") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.refreshInstalledAddons() }, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("Refresh add-ons", modifier = Modifier.padding(horizontal = 12.dp))
            }
            Button(onClick = { vm.startPhoneManagement() }, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text("Manage on phone (QR)", modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        InfoText("${state.installedAddons.size} add-on(s) installed.")
    }
}


// ══════════════════════════ REUSABLE UI ══════════════════════════

/** NuvioTV-style collapsible section card: title + description, "Closed/Open" + chevron. */
@Composable
private fun ExpandableSection(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
    ) {
        Card(
            onClick = onToggle,
            shape = CardDefaults.shape(shape = RoundedCornerShape(18.dp)),
            scale = CardDefaults.scale(focusedScale = 1f),
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color(0xFF23232C)
            ),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (expanded) "Open" else "Closed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) { content() }
        }
    }
}

/** Rounded pill rail item with a leading icon and trailing chevron. */
@Composable
private fun CategoryRailItem(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape = RoundedCornerShape(30.dp)),
        scale = CardDefaults.scale(focusedScale = 1.03f),
        colors = CardDefaults.colors(
            containerColor = if (selected) PillSelected else PillIdle,
            focusedContainerColor = PillSelected
        ),
        border = CardDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, Color.White)) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        modifier = Modifier.fillMaxWidth().height(60.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}


@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(
            onClick = { onToggle(!checked) },
            colors = ButtonDefaults.colors(containerColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
        ) { Text(if (checked) "On" else "Off", modifier = Modifier.padding(horizontal = 12.dp)) }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onClick, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(value, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
}

@Composable
private fun OptionsRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (value, label) ->
                val isSel = value == selected
                Button(
                    onClick = { onSelect(value) },
                    colors = ButtonDefaults.colors(containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                ) { Text(label, modifier = Modifier.padding(horizontal = 8.dp)) }
            }
        }
    }
}

private fun connectionStatus(key: String): String {
    val k = key.trim()
    return when {
        k.isBlank() -> "Not connected"
        k.length <= 4 -> "Connected ••••"
        else -> "Connected ••••${k.takeLast(4)}"
    }
}

@Composable
private fun DebridRow(name: String, subtitle: String, status: String, connected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary))),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status, style = MaterialTheme.typography.labelLarge, color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstalledAddonRow(
    index: Int, name: String, version: String, summary: String, url: String,
    onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("${index + 1}. $name $version".trim(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Button(onClick = onUp, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) { Text("↑") }
        Button(onClick = onDown, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) { Text("↓") }
        Button(onClick = onRemove, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)) { Text("Remove", modifier = Modifier.padding(horizontal = 6.dp)) }
    }
}


// ══════════════════════════ DIALOGS ══════════════════════════

@Composable
private fun DebridKeyDialog(
    providerName: String,
    currentValue: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(currentValue) { mutableStateOf(currentValue) }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(680.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Connect $providerName", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Paste your $providerName API key. Stored on this device only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvTextField(value = text, onValueChange = { text = it }, label = "$providerName API key", placeholder = "Paste key…", modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done, onImeAction = { onSave(text) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(text) }) { Text("Save", modifier = Modifier.padding(horizontal = 12.dp)) }
                Button(onClick = onClear, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("Clear", modifier = Modifier.padding(horizontal = 12.dp)) }
                Button(onClick = onDismiss, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("Cancel", modifier = Modifier.padding(horizontal = 12.dp)) }
            }
        }
    }
}

@Composable
private fun QrManageDialog(url: String, onDismiss: () -> Unit) {
    val bitmap = remember(url) { AddonNetworkUtils.qrBitmap(url, 480) }
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Manage add-ons on your phone", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Scan this code with a phone on the same Wi-Fi, or open the URL below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(260.dp).background(Color.White).padding(8.dp))
            }
            Text(url, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onDismiss) { Text("Done", modifier = Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun AddAddonDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Add a scraper add-on", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text("Paste the add-on's manifest URL (…/manifest.json).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TvTextField(value = text, onValueChange = { text = it }, label = "Manifest URL", placeholder = "https://…/manifest.json", modifier = Modifier.fillMaxWidth(), imeAction = ImeAction.Done, onImeAction = { onSave(text) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onSave(text) }) { Text("Add", modifier = Modifier.padding(horizontal = 12.dp)) }
                Button(onClick = onDismiss, colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Text("Cancel", modifier = Modifier.padding(horizontal = 12.dp)) }
            }
        }
    }
}


/** NuvioTV-style option picker: title + selectable option cards (border + check). */
@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<SettingsViewModel.EngineOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.width(660.dp).background(Color(0xFF101014), RoundedCornerShape(18.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            options.forEach { opt ->
                val isSel = opt.id == selected
                Card(
                    onClick = { onSelect(opt.id) },
                    shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
                    scale = CardDefaults.scale(focusedScale = 1.01f),
                    colors = CardDefaults.colors(
                        containerColor = if (isSel) Color(0xFF23232C) else Color(0xFF1A1A20),
                        focusedContainerColor = Color(0xFF2A2A34)
                    ),
                    border = CardDefaults.border(
                        border = if (isSel) Border(BorderStroke(2.dp, Color(0xFF8A8A93))) else Border.None,
                        focusedBorder = Border(BorderStroke(2.dp, Color.White))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                opt.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isSel) Color(0xFFBFBFC6) else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(opt.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9A9AA2))
                        }
                        if (isSel) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
