package io.github.byjoelsamuel.drivesanity.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import io.github.byjoelsamuel.drivesanity.Prefs

/**
 * "Screens": every dashboard in one list, the current one marked, so any of them is two presses away
 * instead of stepping through the others one at a time.
 */
class ScreenPickerScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template = template(Prefs.layout(carContext)) { picked ->
        Prefs.setLayout(carContext, picked)
        screenManager.pop()
    }

    companion object {
        /** The list for the car, with [current] selected. [onPick] is called with the one chosen. */
        fun template(current: Prefs.Layout, onPick: (Prefs.Layout) -> Unit): Template {
            val layouts = Prefs.Layout.entries
            val list = ItemList.Builder()
            for (l in layouts) list.addItem(Row.Builder().setTitle(l.title).addText(blurb(l)).build())
            list.setSelectedIndex(layouts.indexOf(current))
            list.setOnSelectedListener { index -> onPick(layouts[index]) }
            return ListTemplate.Builder()
                .setHeader(Header.Builder().setTitle("Screens").setStartHeaderAction(Action.BACK).build())
                .setSingleList(list.build())
                .build()
        }

        private fun blurb(l: Prefs.Layout) = when (l) {
            Prefs.Layout.GAUGES -> "Bars for revs, throttle and temperatures"
            Prefs.Layout.MAP -> "Map, speed and revs"
            Prefs.Layout.RETRO -> "Saab-green digital dash"
            Prefs.Layout.DIAG -> "Fault codes and live engine readings"
            Prefs.Layout.LAUNCH -> "0–60 timer"
            Prefs.Layout.MUSIC -> "What's playing"
        }
    }
}
