package com.amitroy.doubletaplauncher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
) {
    /** Stable identifier used for dock persistence. */
    val key: String get() = "$packageName/$activityName"
}

/**
 * Loads the list of launchable apps.
 *
 * Icons are expensive to inflate (a hundred-odd drawables), so the scan runs off the main
 * thread once and is cached until a package is added, removed or changed.
 */
object AppRepository {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cache: List<AppEntry>? = null

    /** Kick off the scan while the user is still looking at the home screen. */
    fun warmUp(context: Context) = load(context) { }

    fun load(context: Context, onReady: (List<AppEntry>) -> Unit) {
        cache?.let { onReady(it); return }
        val app = context.applicationContext
        executor.execute {
            val list = queryAll(app)
            cache = list
            mainHandler.post { onReady(list) }
        }
    }

    fun invalidate() {
        cache = null
    }

    /**
     * Look up a single entry by its dock key, without waiting for the full scan.
     * Returns null if the app has since been uninstalled.
     */
    fun resolve(context: Context, key: String): AppEntry? {
        val slash = key.indexOf('/')
        if (slash <= 0) return null
        val packageName = key.substring(0, slash)
        val activityName = key.substring(slash + 1)
        val pm = context.packageManager
        return runCatching {
            val info = pm.getActivityInfo(ComponentName(packageName, activityName), 0)
            AppEntry(
                label = info.loadLabel(pm).toString(),
                packageName = packageName,
                activityName = activityName,
                icon = info.loadIcon(pm)
            )
        }.getOrNull()
    }

    fun launch(context: Context, entry: AppEntry, source: View? = null) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        val options = source?.let {
            ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle()
        }

        runCatching { context.startActivity(intent, options) }.onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.could_not_open, entry.label),
                Toast.LENGTH_SHORT
            ).show()
            invalidate()
        }
    }

    fun openAppInfo(context: Context, entry: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun requestUninstall(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.fromParts("package", entry.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun queryAll(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .mapNotNull { info ->
                runCatching {
                    AppEntry(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        activityName = info.activityInfo.name,
                        icon = info.loadIcon(pm)
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }
}
