// SPDX-License-Identifier: GPL-3.0-or-later

package `in`.orbitmaps.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import `in`.orbitmaps.core.model.LatLon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The user's position, from Android's own LocationManager (no Google Play Services). It stays on the
 * phone: never logged, stored or sent (PRIVACY.md). Updates run only while someone collects the flow,
 * which the UI does only while the app is visible.
 */
object DeviceLocation {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** LocationManager.FUSED_PROVIDER, added in Android 12; written out so older phones can read it. */
    const val FUSED = "fused"

    private const val MIN_INTERVAL_MS = 1_000L
    private const val MIN_DISTANCE_M = 5f

    /** True if the user allowed precise or approximate location. */
    fun isPermitted(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The providers to listen to: the fused provider when the phone has it (Android 12+), otherwise
     * GPS and network together, so a fix arrives quickly indoors and accurately outdoors.
     */
    fun providers(enabled: List<String>, sdk: Int): List<String> = when {
        sdk >= Build.VERSION_CODES.S && FUSED in enabled -> listOf(FUSED)
        else -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in enabled }
    }

    /** The more useful of two fixes: a newer one, unless it is much less accurate and only a little newer. */
    fun better(candidate: Location, current: Location?): Boolean = isBetter(
        candidateTimeMs = candidate.time,
        candidateAccuracyM = if (candidate.hasAccuracy()) candidate.accuracy else Float.MAX_VALUE,
        currentTimeMs = current?.time,
        currentAccuracyM = if (current?.hasAccuracy() == true) current.accuracy else Float.MAX_VALUE
    )

    /** [better] on plain values, so it can be unit-tested without Android's Location. */
    fun isBetter(
        candidateTimeMs: Long,
        candidateAccuracyM: Float,
        currentTimeMs: Long?,
        currentAccuracyM: Float
    ): Boolean {
        if (currentTimeMs == null) return true
        val newer = candidateTimeMs - currentTimeMs
        return when {
            newer > STALE_MS -> true
            newer < -STALE_MS -> false
            candidateAccuracyM <= currentAccuracyM -> newer >= 0 || candidateAccuracyM < currentAccuracyM
            else -> newer > 0 && candidateAccuracyM - currentAccuracyM < MUCH_LESS_ACCURATE_M
        }
    }

    private const val STALE_MS = 30_000L
    private const val MUCH_LESS_ACCURATE_M = 50f

    /** Location fixes while collected. Emits nothing without permission. */
    @SuppressLint("MissingPermission") // checked by isPermitted() right before requesting updates
    fun updates(context: Context): Flow<Location> = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !isPermitted(context)) {
            close()
            return@callbackFlow
        }
        var best: Location? = null
        val listener = LocationListener { location ->
            if (better(location, best)) {
                best = location
                trySend(location)
            }
        }
        val providers = providers(manager.getProviders(true), Build.VERSION.SDK_INT)
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { last ->
            best = last
            trySend(last)
        }
        providers.forEach {
            manager.requestLocationUpdates(it, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
        }
        awaitClose { manager.removeUpdates(listener) }
    }
}

fun Location.toLatLon(): LatLon? = LatLon.orNull(latitude, longitude)
