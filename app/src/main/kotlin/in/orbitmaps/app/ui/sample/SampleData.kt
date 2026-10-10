// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.ui.sample

import androidx.annotation.StringRes
import `in`.orbitmaps.app.R

/*
 * Placeholder data so every window can be shown before the data layer exists (plan step 12.3).
 * None of it is real: names live in res/values/strings_sample.xml, which is not translated.
 * Replace each list with a repository when its feature is built.
 */

/** How a place or report is labelled (plan step 12.1). */
enum class Status { New, Confirmed, Compiled }

data class SamplePlace(
    val id: String,
    @StringRes val name: Int,
    @StringRes val category: Int,
    val distanceKm: Double,
    val status: Status,
    val confirmations: Int,
    val rating: Double?,
    /** Made-up position inside the Panaji sample region, so a route can be planned to it. */
    val lat: Double,
    val lon: Double
)

data class SampleReport(@StringRes val title: Int, @StringRes val where: Int, val minutesAgo: Int, val stillThere: Int)

data class SampleMember(@StringRes val name: Int, val distanceKm: Double, val minutesAgo: Int)

enum class Delivery { Sent, Relayed, Acknowledged }

data class SampleMessage(
    @StringRes val author: Int,
    @StringRes val text: Int,
    val mine: Boolean,
    val delivery: Delivery
)

object SampleData {
    val places = listOf(
        SamplePlace(
            "cafe", R.string.sample_place_cafe, R.string.category_food, 0.4,
            Status.Confirmed, 6, 4.4, 15.5010, 73.8300
        ),
        SamplePlace(
            "fuel", R.string.sample_place_fuel, R.string.category_fuel, 1.2,
            Status.Confirmed, 3, null, 15.4850, 73.8180
        ),
        SamplePlace(
            "ev", R.string.sample_place_ev, R.string.category_ev, 2.1,
            Status.New, 0, null, 15.5130, 73.8400
        ),
        SamplePlace(
            "trail", R.string.sample_place_trail, R.string.category_trails, 5.8,
            Status.Compiled, 0, 4.7, 15.4550, 73.8050
        ),
        SamplePlace(
            "clinic", R.string.sample_place_clinic, R.string.category_hospital, 1.6,
            Status.Confirmed, 9, null, 15.4930, 73.8250
        )
    )

    fun place(id: String): SamplePlace = places.firstOrNull { it.id == id } ?: places.first()

    val reports = listOf(
        SampleReport(R.string.report_pothole, R.string.sample_where_bridge, 12, 3),
        SampleReport(R.string.report_flooding, R.string.sample_where_market, 40, 1),
        SampleReport(R.string.report_closed_road, R.string.sample_where_riverside, 95, 5)
    )

    val members = listOf(
        SampleMember(R.string.sample_member_asha, 0.0, 1),
        SampleMember(R.string.sample_member_ravi, 1.2, 4),
        SampleMember(R.string.sample_member_meena, 3.5, 9)
    )

    val messages = listOf(
        SampleMessage(R.string.sample_member_ravi, R.string.sample_message_fuel, mine = false, Delivery.Acknowledged),
        SampleMessage(R.string.sample_member_asha, R.string.sample_message_wait, mine = true, Delivery.Relayed),
        SampleMessage(R.string.sample_member_meena, R.string.sample_message_ok, mine = false, Delivery.Sent)
    )
}
