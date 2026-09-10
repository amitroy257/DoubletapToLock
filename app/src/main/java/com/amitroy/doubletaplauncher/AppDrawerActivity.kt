package com.amitroy.doubletaplauncher

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The app drawer, as its own activity.
 *
 * This separation is the whole answer to "it can't tell the home page from the app
 * drawer". There is no state to infer and no flag to keep in sync: while this activity is
 * on top, [HomeActivity] is paused, its views are not in the touch-dispatch path, and the
 * double-tap-to-lock gesture cannot fire. Pressing HOME from here brings the `singleTask`
 * HomeActivity forward, which destroys this activity on the way.
 */
class AppDrawerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppAdapter
    private lateinit var emptyView: TextView

    private var allApps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_drawer)

        prefs = Prefs(this)
        emptyView = findViewById(R.id.emptyView)

        val root = findViewById<View>(R.id.drawerRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        adapter = AppAdapter(
            onClick = { entry, view ->
                AppRepository.launch(this, entry, view)
                finish()
            },
            onLongClick = ::showAppMenu
        )

        findViewById<RecyclerView>(R.id.appGrid).apply {
            layoutManager = GridLayoutManager(this@AppDrawerActivity, SPAN_COUNT)
            adapter = this@AppDrawerActivity.adapter
            setHasFixedSize(true)
        }

        findViewById<EditText>(R.id.searchField).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyFilter(s?.toString().orEmpty())
        })

        AppRepository.load(this) { apps ->
            allApps = apps
            applyFilter("")
        }
    }

    private fun applyFilter(query: String) {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.contains(trimmed, ignoreCase = true) }
        }
        adapter.submit(filtered)
        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAppMenu(entry: AppEntry) {
        val inDock = entry.key in prefs.favourites
        val dockAction = getString(
            if (inDock) R.string.remove_from_dock_action else R.string.add_to_dock_action
        )
        val options = arrayOf(
            dockAction,
            getString(R.string.app_info_action),
            getString(R.string.uninstall_action)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> toggleDock(entry, inDock)
                    1 -> AppRepository.openAppInfo(this, entry)
                    2 -> AppRepository.requestUninstall(this, entry)
                }
            }
            .show()
    }

    private fun toggleDock(entry: AppEntry, currentlyInDock: Boolean) {
        if (currentlyInDock) {
            prefs.removeFavourite(entry.key)
            toast(getString(R.string.removed_from_dock, entry.label))
        } else if (prefs.addFavourite(entry.key)) {
            toast(getString(R.string.added_to_dock, entry.label))
        } else {
            toast(getString(R.string.dock_full, Prefs.MAX_DOCK))
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val SPAN_COUNT = 4
    }
}

private class AppAdapter(
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var items: List<AppEntry> = emptyList()

    @Suppress("NotifyDataSetChanged")
    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.icon.setImageDrawable(entry.icon)
        holder.label.text = entry.label
        holder.itemView.setOnClickListener { onClick(entry, holder.icon) }
        holder.itemView.setOnLongClickListener { onLongClick(entry); true }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
    }
}
