package com.yumi.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumi.launcher.core.Http
import com.yumi.launcher.core.LauncherPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.security.MessageDigest

/**
 * A mod's icon from its catalogue, with the [fallback] glyph until it arrives or if it never does.
 * Icons are kept in memory for the session and on disk between them — a catalogue page is twenty
 * of them, and scrolling back should not fetch any twice.
 */
@Composable
fun RemoteIcon(url: String?, fallback: ImageVector, size: Dp, modifier: Modifier = Modifier) {
    val image by produceState(url?.let(ImageCache::memory), url) {
        if (url != null && value == null) value = ImageCache.load(url)
    }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(fallback, null, Modifier.size(size * 0.5f), tint = scheme.onSurfaceVariant)
    }
}

/** A picture from the web — a screenshot in a mod's gallery — cropped to fill [modifier]'s size. */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    val image by produceState(ImageCache.memory(url), url) {
        if (value == null) value = ImageCache.load(url)
    }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Downloaded images by URL: in memory for the session and, unless told otherwise, on disk between sessions. */
internal object ImageCache {
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 300
    }

    fun memory(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    /** [disk] off for images that change under the same URL, such as a player's skin. */
    suspend fun load(url: String, disk: Boolean = true): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val file = LauncherPaths.cache.resolve("icons").resolve(hash(url))
            val bytes = if (disk && file.isFile) file.readBytes() else Http.bytes(url).also { if (disk) { file.parentFile.mkdirs(); file.writeBytes(it) } }
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()?.also { synchronized(memory) { memory[url] = it } }
    }

    private fun hash(url: String) = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
}
