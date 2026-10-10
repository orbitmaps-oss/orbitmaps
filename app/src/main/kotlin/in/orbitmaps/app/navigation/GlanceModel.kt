// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.navigation

import kotlin.math.ceil

/** The one status the glance screen's single alert slot can show. */
enum class GlanceStatus { None, Waiting, Rerouting, OffRoute, Arrived }

/** Everything the driving screen shows, worked out from the drive state. Pure, so it is unit-tested. */
data class GlanceModel(
    val instruction: String,
    val arrowRotation: Float,
    /** Distance to the next maneuver; null while waiting for a fix and on arrival. */
    val distanceM: Double?,
    val speedKmh: Int?,
    val remainingMinutes: Int,
    val remainingKm: Double,
    val arrivalAtMs: Long,
    val status: GlanceStatus,
    val simulated: Boolean
) {
    companion object {
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60.0
        private const val KMH_PER_MPS = 3.6

        fun from(drive: DriveUi, nowMs: Long): GlanceModel {
            val route = drive.route
            val speed = drive.fix?.speedMps?.let { (it * KMH_PER_MPS).toInt() }
            val nav = drive.nav
            val last = nav as? NavState.OnRoute ?: drive.lastOnRoute
            return when {
                nav == NavState.Arrived -> {
                    val end = route.maneuvers.last()
                    GlanceModel(
                        instruction = end.instruction,
                        arrowRotation = NavFormat.turnRotation(end.type),
                        distanceM = null,
                        speedKmh = speed,
                        remainingMinutes = 0,
                        remainingKm = 0.0,
                        arrivalAtMs = nowMs,
                        status = GlanceStatus.Arrived,
                        simulated = drive.simulated
                    )
                }
                last == null -> {
                    val first = route.maneuvers.first()
                    GlanceModel(
                        instruction = first.instruction,
                        arrowRotation = NavFormat.turnRotation(first.type),
                        distanceM = null,
                        speedKmh = speed,
                        remainingMinutes = minutes(route.timeSeconds),
                        remainingKm = route.lengthM / 1000.0,
                        arrivalAtMs = nowMs + (route.timeSeconds * MS_PER_SECOND).toLong(),
                        status = GlanceStatus.Waiting,
                        simulated = drive.simulated
                    )
                }
                else -> {
                    val step = last.next ?: last.current
                    GlanceModel(
                        instruction = step.instruction,
                        arrowRotation = NavFormat.turnRotation(step.type),
                        distanceM = last.distanceToNextM,
                        speedKmh = speed,
                        remainingMinutes = minutes(last.remainingSeconds),
                        remainingKm = last.remainingM / 1000.0,
                        arrivalAtMs = nowMs + (last.remainingSeconds * MS_PER_SECOND).toLong(),
                        status = when {
                            drive.rerouting -> GlanceStatus.Rerouting
                            drive.rerouteFailed -> GlanceStatus.OffRoute
                            else -> GlanceStatus.None
                        },
                        simulated = drive.simulated
                    )
                }
            }
        }

        private fun minutes(seconds: Double) = ceil(seconds / SECONDS_PER_MINUTE).toInt().coerceAtLeast(0)
    }
}
