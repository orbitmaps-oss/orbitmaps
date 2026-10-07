// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.core.model

/**
 * A WGS 84 coordinate in decimal degrees.
 *
 * Never log or persist instances outside the device: coordinates are personal data.
 */
data class LatLon(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "latitude must be within [$MIN_LATITUDE, $MAX_LATITUDE]"
        }
        require(longitude.isFinite() && longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "longitude must be within [$MIN_LONGITUDE, $MAX_LONGITUDE]"
        }
    }

    // Deliberately omits the values so coordinates never leak into logs or crash messages.
    override fun toString(): String = "LatLon(<redacted>)"

    companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0

        /** Returns a [LatLon], or `null` if the values are out of range or not finite. */
        fun orNull(latitude: Double, longitude: Double): LatLon? =
            if (isValid(latitude, longitude)) LatLon(latitude, longitude) else null

        fun isValid(latitude: Double, longitude: Double): Boolean = latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE
    }
}
