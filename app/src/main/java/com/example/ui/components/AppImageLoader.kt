package com.example.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AppImageLoader {
    private val installed = AtomicBoolean(false)

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        Coil.setImageLoader {
            val client = OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val original = chain.request()
                    val host = original.url.host.lowercase()
                    val builder = original.newBuilder()
                    if (original.header("User-Agent") == null) {
                        builder.header("User-Agent", BROWSER_UA)
                    }
                    if (original.header("Accept") == null) {
                        builder.header("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.8")
                    }
                    if (original.header("Referer") == null) {
                        when {
                            host == "alifcarimport.com" || host.endsWith(".alifcarimport.com") ->
                                builder.header("Referer", "https://alifcarimport.com/")
                            host == "mubarekzeru.github.io" ->
                                builder.header("Referer", "https://mubarekzeru.github.io/AlifCarImport/")
                            com.example.data.network.TikTokThumbnailResolver.isTikTokCdnHost(host) ->
                                builder.header("Referer", "https://www.tiktok.com/")
                        }
                    }
                    chain.proceed(builder.build())
                }
                .build()

            ImageLoader.Builder(appContext)
                .okHttpClient(client)
                .respectCacheHeaders(false)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(appContext.cacheDir.resolve("vehicle_image_cache"))
                        .maxSizeBytes(100L * 1024L * 1024L)
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }
}

private data class DecodedImageCandidate(
    val key: String,
    val data: Any
)

@Composable
fun ResilientAppImage(
    candidateUrls: List<String>,
    contentDescription: String?,
    cacheKeyPrefix: String,
    modifier: Modifier = Modifier,
    width: Int? = null,
    height: Int? = null,
    crossfadeMillis: Int = 180,
    contentScale: ContentScale = ContentScale.Crop,
    onAllCandidatesFailed: (() -> Unit)? = null,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val decodedCandidates = remember(candidateUrls) {
        candidateUrls.mapNotNull { url ->
            decodeImageModel(url)?.let { model ->
                DecodedImageCandidate(
                    key = "${cacheKeyPrefix}_${url.hashCode()}_${url.length}",
                    data = model
                )
            }
        }
    }
    var attemptIndex by remember(decodedCandidates) { mutableIntStateOf(0) }
    val activeCandidate = decodedCandidates.getOrNull(attemptIndex)

    if (activeCandidate != null) {
        val request = remember(activeCandidate.key, width, height, crossfadeMillis) {
            buildAppImageRequest(
                context = context,
                data = activeCandidate.data,
                memoryCacheKey = activeCandidate.key,
                width = width,
                height = height,
                crossfadeMillis = crossfadeMillis
            )
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            onError = {
                if (attemptIndex < decodedCandidates.size) {
                    val next = attemptIndex + 1
                    attemptIndex = next
                    if (next >= decodedCandidates.size) {
                        onAllCandidatesFailed?.invoke()
                    }
                }
            },
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        fallback()
    }
}
