package com.amitroy.doubletaplauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs

/**
 * The home screen.
 *
 * ── Why this is an Activity and not an accessibility overlay ────────────────────
 *
 * The previous version watched the Pixel Launcher from an AccessibilityService and
 * floated an invisible TYPE_ACCESSIBILITY_OVERLAY window over it. That design has two
 * dead ends, and they are exactly the two bugs that were reported:
 *
 *  1. "It thinks the app drawer is the home page."
 *     On a Pixel, the home screen and the app drawer are the SAME activity in the SAME
 *     package (com.google.android.apps.nexuslauncher / NexusLauncherActivity). The drawer
 *     is an internal view that slides up inside that one window. No accessibility event
 *     distinguishes them, so `launcherPackages.contains(packageName)` is true in both.
 *     There is no public API that reports "the Pixel app drawer is open" — it is private
 *     state inside a closed-source app.
 *
 *  2. "A single tap stops working."
 *     A window that receives ACTION_DOWN consumes it. Returning false from an
 *     OnTouchListener only tells the *View* not to handle it; the *window* has already
 *     swallowed the event and it never reaches the launcher underneath.
 *     FLAG_NOT_TOUCH_MODAL only passes through touches that land OUTSIDE the overlay's
 *     bounds. The one flag that would let touches through, FLAG_NOT_TOUCHABLE, also stops
 *     you receiving them. You cannot both observe a tap and forward it — that is a
 *     deliberate anti-tapjacking boundary in Android, not a bug to work around.
 *
 * Being the launcher removes both problems instead of fighting them:
 *
 *  • "Am I on the home page?" becomes "is this activity resumed?" — a fact the OS
 *    guarantees, not a guess. The app drawer is a different activity ([AppDrawerActivity]),
 *    so while it is open this activity is paused and cannot receive a touch at all.
 *
 *  • Touches are dispatched normally through our own view tree. App icons are real
 *    clickable views sitting above the wallpaper layer, so they consume their own taps
 *    and fire instantly. Only taps that hit empty wallpaper reach the gesture detector.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var lockController: LockController
    private lateinit var gestureDetector: GestureDetector

    private lateinit var wallpaperTapTarget: View
    private lateinit var content: View
    private lateinit var dockRow: LinearLayout
    private lateinit var dockHint: TextView

    /**
     * Belt-and-braces for bug #1.
     *
     * Activity lifecycle already makes it impossible to receive a touch here while the
     * drawer is up, so this flag should never actually be the thing that saves us. It is
     * a cheap assertion that the lock can only fire from a genuinely visible home screen.
     */
    private var isHomeResumed = false

    private val swipeDistancePx by lazy { 72f * resources.displayMetrics.density }
    private val swipeVelocityPx by lazy { 250f * resources.displayMetrics.density }

    /** Installed apps changed — drop the cached list so the drawer and dock stay honest. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppRepository.invalidate()
            renderDock()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContentView(R.layout.activity_home)

        prefs = Prefs(this)
        lockController = LockController(this)

        wallpaperTapTarget = findViewById(R.id.wallpaperTapTarget)
        content = findViewById(R.id.content)
        dockRow = findViewById(R.id.dockRow)
        dockHint = findViewById(R.id.dockHint)

        // The wallpaper layer stays edge-to-edge so a double tap works right up to the
        // screen corners; only the UI layer gets inset away from the bars.
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        gestureDetector = GestureDetector(this, WallpaperGestures())

        // Returning true keeps the stream of events coming after ACTION_DOWN. This view
        // is the BOTTOM layer of the FrameLayout, so it only ever sees touches that no
        // icon above it wanted — swallowing those costs nothing.
        wallpaperTapTarget.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        findViewById<View>(R.id.appsButton).setOnClickListener { openAppDrawer() }

        // Home is the bottom of the world. Back must not exit it.
        onBackPressedDispatcher.addCallback(this) { /* deliberately nothing */ }

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        AppRepository.warmUp(this)
    }

    override fun onResume() {
        super.onResume()
        isHomeResumed = true
        renderDock()
    }

    override fun onPause() {
        super.onPause()
        isHomeResumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(packageReceiver) }
    }

    /**
     * Fired when HOME is pressed while we are already the foreground task. Because this
     * activity is `singleTask`, the system has already destroyed anything stacked above
     * us — including the app drawer — before we get here. Nothing left to do.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    // ── Gestures on empty wallpaper ────────────────────────────────────────────────

    private inner class WallpaperGestures : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        /**
         * THE FIX FOR BUG #2.
         *
         * Note what is *not* here: `onSingleTapUp` and `onSingleTapConfirmed` are both
         * left unimplemented. The old code counted taps inside `onSingleTapUp`, which is
         * what created the "one tap starts waiting for a second one" feel.
         *
         * `onDoubleTap` fires on the ACTION_DOWN of the second tap, so the lock is
         * immediate. And because a single tap on bare wallpaper has no action to perform
         * — it does nothing in every launcher ever shipped — there is nothing for the
         * ~300 ms double-tap window to delay. Taps on app icons never reach this listener
         * at all: those views sit above this one and consume their own touches, so they
         * still fire the instant your finger lifts.
         */
        override fun onDoubleTap(e: MotionEvent): Boolean {
            lockScreen()
            return true
        }

        /** Long-press empty space for settings — the standard launcher escape hatch. */
        override fun onLongPress(e: MotionEvent) {
            wallpaperTapTarget.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startActivity(Intent(this@HomeActivity, SetupActivity::class.java))
        }

        /** Swipe up to open the drawer, same as the Pixel Launcher. */
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false
            val dy = e2.y - e1.y
            val dx = e2.x - e1.x
            val isUpwards = dy < -swipeDistancePx && abs(dy) > abs(dx)
            if (isUpwards && abs(velocityY) > swipeVelocityPx) {
                openAppDrawer()
                return true
            }
            return false
        }
    }

    private fun lockScreen() {
        if (!prefs.doubleTapEnabled) return
        if (!isHomeResumed) return

        if (prefs.hapticEnabled) {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            wallpaperTapTarget.performHapticFeedback(effect)
        }

        when (lockController.lock()) {
            LockResult.LOCKED -> Unit // screen is already off
            LockResult.FAILED ->
                Toast.makeText(this, R.string.lock_failed, Toast.LENGTH_SHORT).show()
            LockResult.NOT_CONFIGURED -> {
                Toast.makeText(this, R.string.lock_not_configured, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SetupActivity::class.java))
            }
        }
    }

    private fun openAppDrawer() {
        startActivity(Intent(this, AppDrawerActivity::class.java))
    }

    // ── Dock ──────────────────────────────────────────────────────────────────────

    private fun renderDock() {
        dockRow.removeAllViews()
        val favourites = prefs.favourites
        dockHint.visibility = if (favourites.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        for (key in favourites) {
            val entry = AppRepository.resolve(this, key) ?: continue
            val icon = inflater.inflate(R.layout.item_dock_app, dockRow, false) as ImageView
            icon.setImageDrawable(entry.icon)
            icon.contentDescription = entry.label
            icon.setOnClickListener { AppRepository.launch(this, entry, it) }
            icon.setOnLongClickListener {
                prefs.removeFavourite(key)
                renderDock()
                Toast.makeText(
                    this,
                    getString(R.string.removed_from_dock, entry.label),
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
            dockRow.addView(icon)
        }
    }
}
