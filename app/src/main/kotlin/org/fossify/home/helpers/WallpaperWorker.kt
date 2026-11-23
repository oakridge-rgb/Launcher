package org.fossify.home.helpers

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.home.extensions.config
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Scanner
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Downloads and applies a fresh wallpaper daily, mirroring the Nice Launcher /
 * Olauncher daily-wallpaper feature. Runs every 4h with a CONNECTED constraint;
 * the day-keyed URL lookup makes it effectively a once-a-day change.
 */
class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val config = applicationContext.config
        if (!config.dailyWallpaper) {
            return Result.success()
        }

        val wallType = if (isSystemDarkTheme(applicationContext)) {
            Constants.WALL_TYPE_DARK
        } else {
            Constants.WALL_TYPE_LIGHT
        }

        val wallpaperUrl = getTodaysWallpaper(wallType, config.firstOpenTime)
        if (wallpaperUrl.isEmpty() || wallpaperUrl == config.dailyWallpaperUrl) {
            return Result.success()
        }

        val success = setWallpaper(applicationContext, wallpaperUrl)
        if (success) {
            config.dailyWallpaperUrl = wallpaperUrl
        }
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context, reenqueue: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(4, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                if (reenqueue) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        }

        private fun isSystemDarkTheme(context: Context) =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        private suspend fun setWallpaper(appContext: Context, url: String): Boolean =
            withContext(Dispatchers.IO) {
                val originalImage = getBitmapFromURL(url) ?: return@withContext false
                val wallpaperManager = WallpaperManager.getInstance(appContext)
                val (width, height) = getScreenDimensions(appContext)
                val scaledBitmap = getWallpaperBitmap(originalImage, width, height)

                val success = try {
                    wallpaperManager.setBitmap(scaledBitmap, null, false, WallpaperManager.FLAG_SYSTEM)
                    wallpaperManager.setBitmap(scaledBitmap, null, false, WallpaperManager.FLAG_LOCK)
                    true
                } catch (e: Exception) {
                    false
                }

                try {
                    originalImage.recycle()
                    scaledBitmap.recycle()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                success
            }

        private suspend fun getBitmapFromURL(src: String?): Bitmap? =
            withContext(Dispatchers.IO) {
                var bitmap: Bitmap? = null
                try {
                    val url = URL(src)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input = connection.inputStream
                    bitmap = BitmapFactory.decodeStream(input)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                bitmap
            }

        private suspend fun getWallpaperBitmap(
            originalImage: Bitmap,
            width: Int,
            height: Int
        ): Bitmap = withContext(Dispatchers.IO) {
            val background = Bitmap.createBitmap(width, height)
            val heightScale = height.toFloat() / originalImage.height
            val widthScale = width.toFloat() / originalImage.width
            val scale = maxOf(heightScale, widthScale)
            val scaledWidth = (originalImage.width * scale).toInt()
            val scaledHeight = (originalImage.height * scale).toInt()

            val canvas = Canvas(background)
            canvas.translate((width - scaledWidth) / 2f, (height - scaledHeight) / 2f)
            canvas.drawBitmap(originalImage, null, Rect(0, 0, scaledWidth, scaledHeight), null)
            background
        }

        private fun getScreenDimensions(context: Context): Pair<Int, Int> {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            return Pair(point.x, point.y)
        }

        private suspend fun getTodaysWallpaper(
            wallType: String,
            firstOpenTime: Long
        ): String = withContext(Dispatchers.IO) {
            var wallpaperUrl: String
            try {
                val daysSince = daysSince(firstOpenTime)
                val key = if (daysSince < 10) {
                    String.format(Locale.ENGLISH, "0_%s", daysSince.toString())
                } else {
                    val month = SimpleDateFormat("M", Locale.ENGLISH).format(Date())
                    val day = SimpleDateFormat("d", Locale.ENGLISH).format(Date())
                    String.format(Locale.ENGLISH, "%s_%s", month, day)
                }

                val url = URL(Constants.URL_WALLPAPERS)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()

                val scanner = Scanner(connection.inputStream)
                val stringBuffer = StringBuffer()
                while (scanner.hasNext()) {
                    stringBuffer.append(scanner.nextLine())
                }

                val json = JSONObject(stringBuffer.toString())
                val wallpapers = json.getString(key)
                val wallpapersJson = JSONObject(wallpapers)
                wallpapersJson.getString(wallType)
            } catch (e: Exception) {
                wallpaperUrl = getBackupWallpaper(wallType)
                wallpaperUrl
            }
        }

        private fun getBackupWallpaper(wallType: String): String {
            return if (wallType == Constants.WALL_TYPE_LIGHT) {
                Constants.URL_DEFAULT_LIGHT_WALLPAPER
            } else {
                Constants.URL_DEFAULT_DARK_WALLPAPER
            }
        }

        private fun daysSince(time: Long): Int {
            if (time <= 0L) {
                return 0
            }
            val elapsed = System.currentTimeMillis() - time
            return TimeUnit.MILLISECONDS.toDays(if (elapsed > 0) elapsed else 0).toInt()
        }

        @Suppress("unused")
        private fun isTablet(context: Context): Boolean {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            val widthInches = metrics.widthPixels / metrics.xdpi
            val heightInches = metrics.heightPixels / metrics.ydpi
            val diagonalInches = sqrt(
                widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0)
            )
            return diagonalInches >= 7.0
        }
    }
}
