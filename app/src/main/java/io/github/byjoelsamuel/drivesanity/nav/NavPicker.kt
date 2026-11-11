package io.github.byjoelsamuel.drivesanity.nav

import android.app.Activity
import android.app.AlertDialog
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import io.github.byjoelsamuel.drivesanity.DriveEngine

/** Phone dialogs: "where to?", pick a search result, or end the current route. */
fun showNavigate(activity: Activity) {
    val dest = Nav.destination
    if (dest != null) {
        AlertDialog.Builder(activity)
            .setTitle("Navigating")
            .setMessage(dest.name)
            .setPositiveButton("End route") { _, _ -> Nav.stop() }
            .setNeutralButton("New destination") { _, _ -> askDestination(activity) }
            .setNegativeButton("Close", null)
            .show()
        return
    }
    askDestination(activity)
}

private fun askDestination(activity: Activity) {
    val input = EditText(activity).apply {
        hint = "Address or place"
        setSingleLine()
        imeOptions = EditorInfo.IME_ACTION_SEARCH
    }
    val pad = (20 * activity.resources.displayMetrics.density).toInt()
    val box = FrameLayout(activity).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }
    val dialog = AlertDialog.Builder(activity)
        .setTitle("Where to?")
        .setView(box)
        .setPositiveButton("Search") { _, _ -> search(activity, input.text.toString()) }
        .setNegativeButton("Cancel", null)
        .create()
    input.setOnEditorActionListener { _, action, _ ->
        if (action == EditorInfo.IME_ACTION_SEARCH) {
            dialog.dismiss()
            search(activity, input.text.toString())
            true
        } else {
            false
        }
    }
    dialog.show()
}

private fun search(activity: Activity, query: String) {
    if (query.isBlank()) return
    Toast.makeText(activity, "Searching…", Toast.LENGTH_SHORT).show()
    Nav.search(query.trim(), DriveEngine.snapshot().position) { result ->
        if (activity.isFinishing) return@search
        result.onFailure {
            Toast.makeText(activity, "Search didn't work: ${it.message}", Toast.LENGTH_LONG).show()
        }.onSuccess { places ->
            if (places.isEmpty()) {
                Toast.makeText(activity, "Nothing found for \"$query\"", Toast.LENGTH_SHORT).show()
                return@onSuccess
            }
            AlertDialog.Builder(activity)
                .setTitle("Pick a destination")
                .setItems(places.map { it.name }.toTypedArray()) { _, i ->
                    Nav.start(places[i])
                    Toast.makeText(activity, "Finding a route…", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
