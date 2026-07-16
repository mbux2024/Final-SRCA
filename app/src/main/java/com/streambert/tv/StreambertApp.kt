package com.streambert.tv

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streambert.tv.di.AppContainer

/**
 * Application entry point. Holds a lightweight manual dependency container
 * (no Hilt/Dagger to keep the build simple) that exposes repositories to
 * ViewModels via [AppContainer].
 *
 * Provides a Coil [ImageLoader] with animated-GIF support for the Service logos.
 * Coil decodes each GIF to the target view size, so keeping the Service cards
 * small keeps the animated bitmaps cheap; combined with RGB_565 this stays well
 * within budget on low-RAM TV hardware.
 */
class StreambertApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Coil image loader tuned to stay light on TV hardware (often 1–2 GB RAM):
     *  - RGB_565 halves the memory each poster/backdrop bitmap occupies (they have
     *    no transparency), which cuts GC pressure and scroll jank dramatically.
     *  - A bounded in-memory cache (~20% of app heap) keeps a warm working set
     *    without ballooning; a disk cache means posters are decoded/downloaded once.
     *  - TMDB images are immutable, so we skip cache-header revalidation.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
                // Rating-source brand logos (IMDb, Trakt, TMDB, Letterboxd, RT) are SVG.
                add(SvgDecoder.Factory())
            }
            .allowRgb565(true)
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
}
