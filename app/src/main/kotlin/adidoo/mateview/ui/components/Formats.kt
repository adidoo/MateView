// SPDX-License-Identifier: AGPL-3.0-or-later
package adidoo.mateview.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "dim. 15 nov., 14:05": a moment within the last year or so. */
fun formatDayTime(timeMs: Long): String =
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(timeMs))

/** "15 nov. 2026, 14:05". */
fun formatFullDateTime(timeMs: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timeMs))

fun formatTime(timeMs: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))

/** "1 h 05", "45 min", or [NO_VALUE]. */
fun formatMinutes(minutes: Double?): String {
    if (minutes == null) return NO_VALUE
    val total = minutes.toLong()
    return if (total < 60) "$total min" else "%d h %02d".format(total / 60, total % 60)
}

/** "44 → 38 %" for battery levels, or [NO_VALUE] when both are missing. */
fun formatSocRange(start: Double?, end: Double?): String =
    if (start == null && end == null) NO_VALUE else "${formatNumber(start)} → ${formatNumber(end)} %"

/** A cost with two decimals, in whatever currency it was entered in TeslaMate. */
fun formatCost(cost: Double?): String = formatNumber(cost, 2)
