# Streambert TV (Google TV / Android TV)

A native **Android TV / Google TV** app, rebuilt from the Streambert desktop
(Electron) project. It keeps the TMDB-powered catalog but replaces the desktop
streaming/embed sources with **TorBox** as the default source, and **auto-plays**
the selected movie or episode through Media3 / ExoPlayer.

This is a standalone Gradle project living in `android-tv/`. It does **not**
share code with the Electron app (different platform/runtime); it re-implements
the relevant logic in Kotlin.

## Highlights

- **Jetpack Compose for TV** (`androidx.tv:tv-material3`) — full D-pad / remote
  navigation with focus highlighting.
- **TorBox-powered streaming via Stremio addons (Torrentio / Comet).** The app
  doesn't scrape torrents itself; it asks your configured addon — which already
  holds your TorBox key — for streams. Selecting a title:
  1. resolves the TMDB id → IMDb id,
  2. queries the addon's stream endpoint (`stream/{movie|series}/{id}.json`),
  3. picks the best **debrid-resolved** stream (prefers cached + your preferred
     quality, de-prioritises CAM/TS),
  4. and **auto-plays** the returned direct URL in ExoPlayer.
- **Two source modes:**
  - **Torrentio + debrid** (default): enter your **TorBox** and/or **Real-Debrid**
    key — the app queries **both** and plays whichever has an instant cached copy
    (each source is tagged TorBox/RD in the picker).
  - **Custom (Comet) URL**: paste a fully-configured addon URL in Settings.
- **Custom player controls** (Netflix-style overlay): back, play/pause, ±10s,
  a **subtitle** picker and **audio-track** picker, **next episode** (for TV),
  the title, a progress bar with times, and a badge that pops up showing
  **HDR / Dolby Vision / Dolby Atmos / DTS:X** when detected in the stream.
- **Media3 / ExoPlayer** playback with the standard TV transport controls, tuned
  for **Dolby Vision / HDR10 / HDR10+ / HLG** video and **Dolby Atmos / Dolby
  Digital(+) / DTS / DTS-HD / DTS:X / TrueHD** audio passthrough (see below).
- **Netflix-style home UI** with a top nav — **Home · Shows · Movies · My List** —
  a **fixed top preview** that shows the backdrop + rating of whatever title you're
  focused on, landscape rows, and **Top 10** rows with big rank numerals. Home rows:
  Services, Trending Movies, Trending TV Shows, Top 10 Movies/TV Today, Airing Today
  in the U.S., Top Rated Movies, Top Rated TV Shows, and a **Genres** row (cover-art
  cards) that opens all movies + TV in that genre.
- **Services row**: a branded row (Netflix, Disney+, Apple TV+, Prime Video,
  Max, Hulu, Paramount+, Peacock, Starz, Discovery+) using **animated GIF logos**
  (from your Media-Data repo). Selecting a service opens its own screen with that
  provider's Popular & Top Rated **movies and TV shows** (via TMDB watch-provider
  discovery). Requires the Coil GIF decoder (wired in `StreambertApp`).
- **My List**: add or remove a title from its detail page; saved titles appear
  in the **My List** tab.
- **Resume / Continue Watching**: playback position is saved per movie/episode;
  a Continue Watching row (real landscape stills + progress bars) resumes right
  where you left off. Individual episodes can be marked **watched/unwatched**.
- **TorBox Instant**: the app verifies TorBox cached (instant) availability for each
  source via `checkcached` and plays instantly-cached streams first, so **Play**
  starts immediately with no download wait (sources are marked **⚡ INSTANT**).
- **TorBox + Real-Debrid instant scraper add-ons** (Nuvio-style direct resolving):
  in addition to debrid-backed Torrentio, you can add **scraper** add-ons that
  return raw torrent hashes with no debrid baked in — **Torrentio (no-debrid)** and
  **Comet for the weebs** are wired by default. When a debrid key is set, the app
  resolves the chosen release to a direct stream: **TorBox** is tried first
  (`checkcached` instant → `createtorrent` → `requestdl`), then **Real-Debrid** as a
  fallback (`addMagnet` → `selectFiles` → `unrestrict`). Whichever has the release
  cached plays instantly. Both services can be connected at once.
- **Debrid services in Settings** (tap to connect): a **Debrid services** section
  lists **TorBox** and **Real-Debrid** as tappable rows showing their connection
  status; tap one to paste its API key in a dialog. Keys are stored on-device only.
- **MPV player engine (default)**: playback uses **libmpv** (via the
  `io.github.abdallahmehiz:mpv-android-lib` artifact — bundles the native `.so`
  binaries, no NDK build needed), the same engine NuvioTV uses. MPV decodes
  virtually every container/codec — **HEVC 10-bit, Dolby Vision, AV1, VP9,
  DTS / DTS-HD / TrueHD, ASS/SSA subtitles** — which fixes the black-screen /
  audio-only problems seen with plain ExoPlayer on many TV devices. The player
  has its own D-pad control overlay (play/pause, ±10s, progress, next episode,
  audio + subtitle pickers from the mpv track list, side-loaded add-on subtitles).
  You can switch back to the built-in **ExoPlayer** engine under **Settings →
  Video player engine** if a specific file misbehaves on MPV. (Our own wrapper
  code against the public `is.xyz.mpv` API — NuvioTV's GPL source is not reused.)
- **Detail page** (ARVIO-style): title, meta, IMDb rating, overview, a **Cast** row
  (director + actors), and an action row — **Play** (auto-plays the best source),
  a **source/quality picker** icon (opens the sources overlay), **Trailer**,
  **watched/unwatched** toggle, and **My List** toggle.
- **Trailers**: detail pages show a **Trailer** button (when TMDB has one) — gated by
  the **TMDB Enrichment → Trailers** toggle. The trailer's YouTube id (from TMDB) is
  resolved to a **direct stream** via an in-app InnerTube extractor that tries several
  clients (ANDROID_VR → ANDROID → iOS) across multiple ranked candidates, then plays
  in the **native MPV player** — no WebView IFrame player. NuvioTV-style.
- **Settings** are organized into NuvioTV-style categories (left rail): **Playback**
  (engine, hardware decoding, tunneling, preferred audio/subtitle language, subtitle
  size, quality, autoplay), **Content Discovery** (TMDB token + metadata **enrichment
  on/off controls**: artwork, basic info, details, cast, trailers, more-like-this,
  collections), **Integration** (TorBox / Real-Debrid tap-to-connect, **MDBList** enable
  + key + **per-provider rating toggles**: IMDb/RT/Audience/Metacritic/TMDB/Trakt/
  Letterboxd, OMDb key), **Add-ons** (stream source, **installed scraper add-ons** you
  can add / remove / reorder, and the subtitles add-on), and **About**.
- **Add-ons manager** (Stremio): install add-ons after install by pasting a manifest
  URL; each installed add-on shows its **name/version/type** (fetched from its
  `manifest.json`), and can be **removed**, **reordered** (priority), and **refreshed**.
  **Manage on phone via QR** — tap *Manage on phone* to start an on-device web server;
  the TV shows a **QR code** to scan from a phone on the same Wi-Fi and manage add-ons
  from the browser (NanoHTTPD + ZXing). Catalog browsing on Home remains TMDB-powered.
- **Subtitles via add-ons**: the player side-loads subtitles from the configured
  **OpenSubtitles v3 Pro** Stremio subtitles add-on (`/subtitles/{type}/{id}.json`),
  shown with proper **language names** (e.g. "Portuguese (Brazil)") in the picker.
  Subtitle **size** (Small/Medium/Large) is configurable in Settings.
- **MDBList ratings** (TMDB enrichment): add an **MDBList API key** in Settings to
  show multi-source ratings on the detail page — **IMDb, Rotten Tomatoes, Audience,
  Metacritic, TMDB, Trakt, Letterboxd** — as a row of chips. MDBList's IMDb rating
  also fills in the IMDb score when no OMDb key is set.
- **Playback settings**: **MPV hardware decoding** toggle (force software decode if a
  device shows garbled video), and **preferred audio / subtitle language** that
  auto-select matching tracks.
- **Auto-play next episode** (Netflix-style): as the credits start (last ~40s of the
  episode) an **"Up next" countdown** appears and rolls into the next episode; only
  when a next episode exists in the season. Toggle in **Playback** settings.
- **Skip Intro** (TV episodes): a Netflix-style **Skip Intro** button shows early in
  the episode and jumps past the intro. Toggle in **Playback** settings. (Heuristic
  window — no per-episode intro markers.)
- **Sources / quality side panel** on the detail screen: **Play** auto-plays the
  best match, or pick a specific release from the side list. Each source shows
  **quality badges** parsed from the release — resolution (4K/1080p/720p), **HDR /
  Dolby Vision**, video codec (HEVC/AV1/H.264), audio (Atmos/TrueHD/DTS:X/DTS-HD/
  DD+/DTS/DD/AAC), channels (5.1/7.1), source (REMUX/BluRay/WEB-DL) and a ⚡ cached
  marker. D-pad **right** from the content hands off into the panel; **left** returns.
- Browse Trending Movies / Series / Top Rated, full-text search, and
  movie/series detail with season + episode pickers.

## Project layout

```
android-tv/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml           # leanback + LEANBACK_LAUNCHER
        ├── res/                          # vector icon, TV banner, theme
        └── java/com/streambert/tv/
            ├── StreambertApp.kt          # Application + manual DI container
            ├── MainActivity.kt
            ├── di/AppContainer.kt
            ├── data/
            │   ├── NetworkModule.kt      # Retrofit/OkHttp for TMDB + addons
            │   ├── settings/             # DataStore-backed settings
            │   ├── tmdb/                 # TMDB api + models + repository
            │   ├── stream/               # Stremio addon api + StreamRepository (playback)
            │   ├── torbox/               # Direct TorBox API client (optional/unused fallback)
            │   └── model/                # CatalogItem, MediaType
            └── ui/
                ├── theme/  navigation/  components/
                ├── setup/  home/  search/  detail/  settings/  player/
```

## Requirements

- **Android Studio** (Ladybug or newer) with the Android SDK (API 34).
- A **TMDB v4 read-access token** (same key the desktop app uses).
- A **TorBox account + API key** (from your TorBox account → Settings → API).
- For the **Custom** source mode: a configured **Comet** (or other Stremio
  addon) URL that already has your TorBox key baked in.

## Building

> This project was authored without an Android SDK available in the build
> environment, so it has **not been compiled here**. Build it locally:

1. Open the `android-tv/` folder in Android Studio (open it as the project
   root, not the repository root).
2. Let Gradle sync. If the Gradle wrapper jar is missing, generate it once:
   ```bash
   cd android-tv
   gradle wrapper --gradle-version 8.9
   ```
   (Android Studio also offers to do this automatically.)
3. Build / run on a Google TV emulator or device:
   ```bash
   ./gradlew :app:assembleDebug          # produces app/build/outputs/apk/debug/app-debug.apk
   ./gradlew :app:installDebug           # install on a connected ADB device
   ```

### First run

On first launch you'll be asked for your **TMDB token** and **TorBox key**.
With those set, the default **Torrentio + TorBox** source works immediately.
You can change everything later in **Settings**, including:

- **Stream source** — Torrentio (auto-built from your TorBox key) or Custom.
- **Custom addon URL** — paste a fully-configured Comet manifest/addon URL,
  e.g. `https://comet.elfhosted.com/<config>/manifest.json` (the app strips the
  trailing `manifest.json` automatically).
- **Auto-play on select** and **preferred quality** (used to rank streams).

## HDR / Dolby Vision & surround audio (Atmos / DTS:X)

Playback is configured in `ui/player/PlaybackFactory.kt`:

- **Video (Dolby Vision, HDR10, HDR10+, HLG):** the app uses the device's
  hardware video decoders + a `SurfaceView`, and enables **tunneled playback**
  (the recommended path for 4K/HDR/DV on Android TV & Google TV). HDR/DV only
  engages when **all** of these support it: the device/SoC decoder, the app's
  video output, the HDMI link, and the TV. On a Chromecast/Google TV 4K, Shield,
  Fire TV, etc. this works out of the box for streams that carry HDR/DV.
  Tunneling can be turned **off per-device in Settings** if a device shows a
  black screen or A/V sync issues.

- **Audio (Atmos / Dolby Digital+ / DTS / DTS-HD / DTS:X / TrueHD):** ExoPlayer
  **bit-streams (passes through)** these formats untouched to a connected
  AVR/soundbar when the HDMI/eARC sink reports support, instead of downmixing to
  PCM stereo. This is automatic based on the device's `AudioCapabilities`.
  - Atmos rides on **E-AC3 JOC** (and TrueHD); both pass through with core Media3.
  - **DTS:X / DTS-HD** pass through fine to a DTS-capable receiver.
  - If you are **not** using a passthrough receiver and need the device itself to
    *decode* DTS to PCM, that requires the optional **`media3-decoder-ffmpeg`**
    extension, which is not published as a prebuilt artifact — you must build it
    from the AndroidX Media source and add it to `app/build.gradle.kts`. Dolby
    (AC3/E-AC3) decoding is handled by the platform on most TV devices.

If a title plays but you only get stereo, check that the device audio output is
set to **"Passthrough / Auto"** (not "PCM/Stereo") in the Android TV sound
settings and that your receiver is connected via HDMI/eARC.

## Supported formats

Handled by Media3 + the modules wired in `app/build.gradle.kts` and
`ui/player/PlaybackFactory.kt`:

| Category | Supported |
|---|---|
| **Video** | H.264/AVC, H.265/HEVC, VP9, AV1, Dolby Vision (+ HDR10 / HDR10+ / HLG) |
| **Audio** | AAC, AC-3 & E-AC-3 (Dolby Digital / Digital+), Dolby Atmos (E-AC3 JOC / TrueHD), DTS, DTS-HD, DTS:X, TrueHD |
| **Containers** | MKV, MP4, WebM, TS (core extractors) |
| **Streaming** | Progressive HTTP, HLS, DASH, SmoothStreaming |
| **Other** | 4K/UHD, multi audio-track & multi subtitle-track selection |

How it works in practice:

- **Video** is decoded by the device's `MediaCodec` decoders. H.264/H.265/VP9 are
  universal; **AV1** uses a hardware decoder when present, otherwise the
  platform's built-in software AV1 decoder (Android 10+). **Dolby Vision** needs
  a DV-capable device + display.
- **Audio**: AAC and Dolby (AC-3/E-AC-3) decode on-device on virtually all TV
  hardware. **Dolby Atmos, TrueHD, DTS, DTS-HD and DTS:X** are **bit-streamed
  (passthrough)** to a capable AVR/soundbar. If you have no passthrough receiver
  and need the device itself to *decode* **DTS** to PCM, that one codec requires
  the optional **`media3-decoder-ffmpeg`** NDK extension (build from AndroidX
  Media source and add to `app/build.gradle.kts`); everything else works with the
  published artifacts.
- **Containers/streaming** are auto-detected by `DefaultMediaSourceFactory`
  (progressive vs HLS/DASH/SmoothStreaming) over an OkHttp HTTP stack.

## Important notes & caveats



- **How playback resolves:** the app calls your addon's
  `stream/{movie|series}/{id}.json` endpoint (series id format `tt…:S:E`) and
  plays the first cached, debrid-resolved `url` that best matches your
  preferred quality. The addon (Torrentio/Comet), configured with your TorBox
  key, does the torrent scraping and TorBox resolution.
- **Un-cached releases:** if the addon only returns un-resolved entries
  (infoHash but no `url`), the app reports that nothing is cached on TorBox yet
  — try a different title/quality or retry shortly after (TorBox needs a moment
  to cache it).
- **Torrentio rate limits:** the public Torrentio instance can rate-limit heavy
  use. If lookups start failing, switch to the Custom mode with your own
  Comet/self-hosted addon URL.
- **The `data/torbox/` direct client is optional.** Playback now goes through
  the Stremio addon; the direct TorBox API client is left in the codebase as an
  alternative resolver but is not wired into the player.
- **No downloads / no ad-block:** this TV build only does streaming; the desktop
  app's download manager, ad/tracker blocking, and embed sources were dropped.
- **Legal:** you are responsible for how you use TorBox and what you stream.
```
