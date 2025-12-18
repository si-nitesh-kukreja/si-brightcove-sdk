import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a dominant color from an image URL
 */
@Composable
fun rememberDominantColor(imageUrl: String): Color {
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(Color.White) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .build()

                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.drawable
                        if (drawable is BitmapDrawable) {
                            val bitmap = drawable.bitmap
                            if (bitmap != null && !bitmap.isRecycled) {
                                // Extract dominant color from bitmap
                                val pixels = IntArray(bitmap.width * bitmap.height)
                                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

                                // Calculate average color
                                var r = 0L
                                var g = 0L
                                var b = 0L
                                var count = 0

                                // Sample pixels (every 10th pixel for performance)
                                for (i in pixels.indices step 10) {
                                    val pixel = pixels[i]
                                    r += android.graphics.Color.red(pixel)
                                    g += android.graphics.Color.green(pixel)
                                    b += android.graphics.Color.blue(pixel)
                                    count++
                                }

                                if (count > 0) {
                                    val avgR = (r / count).toInt()
                                    val avgG = (g / count).toInt()
                                    val avgB = (b / count).toInt()

                                    // Ensure good contrast by adjusting luminance
                                    val luminance = ColorUtils.calculateLuminance(
                                        android.graphics.Color.rgb(avgR, avgG, avgB)
                                    )

                                    // If the color is too dark, lighten it; if too light, darken it
                                    val finalColor = if (luminance < 0.5) {
                                        // Lighten dark colors
                                        ColorUtils.blendARGB(
                                            android.graphics.Color.rgb(avgR, avgG, avgB),
                                            android.graphics.Color.WHITE,
                                            0.3f
                                        )
                                    } else {
                                        // Darken light colors slightly for better visibility
                                        ColorUtils.blendARGB(
                                            android.graphics.Color.rgb(avgR, avgG, avgB),
                                            android.graphics.Color.BLACK,
                                            0.1f
                                        )
                                    }

                                    dominantColor = Color(finalColor)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to white if extraction fails
                    dominantColor = Color.White
                }
            }
        }
    }

    return dominantColor
}