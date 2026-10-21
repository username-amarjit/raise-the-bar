package com.raisethebar.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raisethebar.app.ui.theme.Rtb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows a photo bundled in the app's assets (works offline); a labelled placeholder when there is none. */
@Composable
fun AssetImage(path: String?, description: String, placeholder: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value =
            path?.let { p ->
                withContext(Dispatchers.IO) {
                    runCatching { context.assets.open(p).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
                }
            }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = description, contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        Box(modifier.clip(shape).background(Rtb.colors.surface2), contentAlignment = Alignment.Center) {
            Text(placeholder, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Rtb.colors.muted)
        }
    }
}
