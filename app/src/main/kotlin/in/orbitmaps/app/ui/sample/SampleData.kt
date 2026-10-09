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
    val rating: Double?
)

data class SampleReport(@StringRes val title: Int, @StringRes val where: Int, val minutesAgo: Int, val stillThere: Int)

data class SampleMember(@StringRes val name: Int, val distanceKm: Double, val minutesAgo: Int)

data class SampleRegion(@StringRes val name: Int, val sizeMb: Int, val installed: Boolean)

enum class Delivery { Sent, Relayed, Acknowledged }

data class SampleMessage(
    @StringRes val author: Int,
    @StringRes val text: Int,
    val mine: Boolean,
    val delivery: Delivery
)

object SampleData {
    val places = listOf(
        SamplePlace("cafe", R.string.sample_place_cafe, R.string.category_food, 0.4, Status.Confirmed, 6, 4.4),
        SamplePlace("fuel", R.string.sample_place_fuel, R.string.category_fuel, 1.2, Status.Confirmed, 3, null),
        SamplePlace("ev", R.string.sample_place_ev, R.string.category_ev, 2.1, Status.New, 0, null),
        SamplePlace("trail", R.string.sample_place_trail, R.string.category_trails, 5.8, Status.Compiled, 0, 4.7),
        SamplePlace("clinic", R.string.sample_place_clinic, R.string.category_hospital, 1.6, Status.Confirmed, 9, null)
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

    val regions = listOf(
        SampleRegion(R.string.sample_region_panaji, 12, installed = true),
        SampleRegion(R.string.sample_region_goa, 180, installed = false),
        SampleRegion(R.string.sample_region_karnataka, 920, installed = false)
    )

    val messages = listOf(
        SampleMessage(R.string.sample_member_ravi, R.string.sample_message_fuel, mine = false, Delivery.Acknowledged),
        SampleMessage(R.string.sample_member_asha, R.string.sample_message_wait, mine = true, Delivery.Relayed),
        SampleMessage(R.string.sample_member_meena, R.string.sample_message_ok, mine = false, Delivery.Sent)
    )
}
