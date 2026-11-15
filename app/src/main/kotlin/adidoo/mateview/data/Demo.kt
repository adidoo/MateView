// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.data

/**
 * Screenshot mode, for debug builds only: place names and the car's name are
 * replaced by generic ones, so that screenshots can be published. It is switched
 * on by creating an empty file named `demo` in the app's private files directory
 * (`adb shell run-as adidoo.mateview touch files/demo`) and restarting the app.
 */
object Demo {
    @Volatile
    var enabled: Boolean = false

    const val CAR_NAME = "Model Y"

    /** The result columns that hold a place name. */
    val PLACE_COLUMNS = setOf(
        "start_address", "end_address", "address", "place", "start_place", "end_place", "name", "location", "city",
    )

    private val pool = listOf(
        "Office", "School", "Supermarket", "Gym", "Train station", "Restaurant", "Cinema", "Friends",
        "Airport", "Harbour", "Park", "Mall", "Stadium", "Library", "Bakery", "Pharmacy",
    )
    private val aliases = LinkedHashMap<String, String>()

    /** A generic name for [place], the same one each time within a run. */
    @Synchronized
    fun alias(place: String): String {
        if (place.isBlank()) return place
        if (place.equals("Maison", ignoreCase = true) || place.equals("Home", ignoreCase = true)) return "Home"
        return aliases.getOrPut(place) { pool.getOrNull(aliases.size) ?: "Place ${aliases.size + 1}" }
    }
}
