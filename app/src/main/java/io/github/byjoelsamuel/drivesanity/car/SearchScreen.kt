package io.github.byjoelsamuel.drivesanity.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import io.github.byjoelsamuel.drivesanity.DriveEngine
import io.github.byjoelsamuel.drivesanity.Prefs
import io.github.byjoelsamuel.drivesanity.nav.Nav
import io.github.byjoelsamuel.drivesanity.nav.Place

/**
 * "Where to?" on the car screen. Android Auto shows its own keyboard (only while parked) and a
 * microphone for speaking the place; the app gets the words back and looks them up. Results are
 * only fetched when the words are submitted, not on every letter, because the free address
 * service doesn't allow search-as-you-type.
 */
class SearchScreen(carContext: CarContext) : Screen(carContext) {
    private var results: List<Place> = emptyList()
    private var loading = false
    private var note: String? = null

    override fun onGetTemplate(): Template {
        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) = search(searchText)
        }
        val builder = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Where to?")
            .setShowKeyboardByDefault(true)
        if (loading) return builder.setLoading(true).build()

        val list = ItemList.Builder()
        if (Nav.active) {
            list.addItem(
                Row.Builder().setTitle("End route").addText("Stop navigating")
                    .setOnClickListener {
                        Nav.stop()
                        CarToast.makeText(carContext, "Route ended", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    }.build(),
            )
        }
        note?.let { list.addItem(Row.Builder().setTitle(it).build()) }
        for (p in results.take(MAX_RESULTS)) {
            val title = p.name.substringBefore(',').trim().ifEmpty { p.name }
            val rest = p.name.substringAfter(',', "").trim()
            val row = Row.Builder().setTitle(title)
            if (rest.isNotEmpty()) row.addText(rest)
            list.addItem(row.setOnClickListener { go(p) }.build())
        }
        return builder.setItemList(list.build()).build()
    }

    private fun search(text: String) {
        val words = text.trim()
        if (words.isEmpty()) return
        loading = true
        note = null
        invalidate()
        Nav.search(words, DriveEngine.snapshot().position) { r ->
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@search
            loading = false
            r.onSuccess {
                results = it
                note = if (it.isEmpty()) "Nothing found for \"$words\"" else null
            }.onFailure {
                results = emptyList()
                note = "Couldn't search. Is the phone online?"
            }
            invalidate()
        }
    }

    private fun go(place: Place) {
        Nav.start(place)
        Prefs.setLayout(carContext, Prefs.Layout.MAP)
        CarToast.makeText(carContext, "Navigating to ${place.name.substringBefore(',')}", CarToast.LENGTH_LONG).show()
        screenManager.pop()
    }

    private companion object {
        const val MAX_RESULTS = 6
    }
}
