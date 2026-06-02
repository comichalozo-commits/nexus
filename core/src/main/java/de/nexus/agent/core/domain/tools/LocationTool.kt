package de.nexus.agent.core.domain.tools

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.nexus.agent.core.data.model.ToolParameterSchema
import de.nexus.agent.core.data.model.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Location information including coordinates and address.
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val address: String?,
    val city: String?,
    val country: String?
)

/**
 * Tool for getting the current device location and reverse geocoding coordinates.
 *
 * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission.
 */

class LocationTool  constructor(
     private val context: Context
) : BaseTool() {

    override val name: String = "location"
    override val description: String =
        "Get the current device location or reverse geocode coordinates to an address. " +
            "Actions: getCurrent (get GPS coordinates and address), getAddress (reverse geocode lat/lng to address)."
    override val parameters: ToolParameterSchema = ToolParameterSchema(
        type = "object",
        properties = mapOf(
            "action" to ToolProperty(
                type = "string",
                description = "The action to perform",
                enum = listOf("getCurrent", "getAddress")
            ),
            "latitude" to ToolProperty(
                type = "string",
                description = "Latitude for reverse geocoding (required for getAddress action)"
            ),
            "longitude" to ToolProperty(
                type = "string",
                description = "Longitude for reverse geocoding (required for getAddress action)"
            )
        ),
        required = listOf("action")
    )

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    override suspend fun execute(arguments: String): String {
        val action = getStringParam(arguments, "action")

        return when (action) {
            "getCurrent" -> getCurrentLocation()
            "getAddress" -> {
                val latStr = getStringParam(arguments, "latitude")
                val lngStr = getStringParam(arguments, "longitude")
                if (latStr.isBlank() || lngStr.isBlank()) {
                    return "Error: latitude and longitude are required for getAddress action."
                }
                val lat = latStr.toDoubleOrNull()
                val lng = lngStr.toDoubleOrNull()
                if (lat == null || lng == null) {
                    return "Error: Invalid coordinates. Latitude and longitude must be numbers."
                }
                reverseGeocode(lat, lng)
            }
            else -> "Error: Unknown action '$action'. Use getCurrent or getAddress."
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): String {
        return try {
            val location = suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(
                            Exception("Location is null. Make sure location services are enabled.")
                        )
                    }
                }.addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }

            val addressInfo = reverseGeocodeInternal(location.latitude, location.longitude)
            val info = LocationInfo(
                latitude = location.latitude,
                longitude = location.longitude,
                address = addressInfo.third,
                city = addressInfo.first,
                country = addressInfo.second
            )

            formatLocationInfo(info)
        } catch (e: SecurityException) {
            "Permission denied. Please grant location permission."
        } catch (e: Exception) {
            "Error getting location: ${e.message}"
        }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String {
        return try {
            val (city, country, address) = reverseGeocodeInternal(latitude, longitude)
            val info = LocationInfo(
                latitude = latitude,
                longitude = longitude,
                address = address,
                city = city,
                country = country
            )
            formatLocationInfo(info)
        } catch (e: Exception) {
            "Error reverse geocoding: ${e.message}"
        }
    }

    private suspend fun reverseGeocodeInternal(
        latitude: Double,
        longitude: Double
    ): Triple<String?, String?, String?> = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val city = addr.locality ?: addr.subAdminArea
                val country = addr.countryName
                val fullAddress = buildString {
                    addr.getAddressLine(0)?.let { append(it) }
                }.ifBlank { null }
                Triple(city, country, fullAddress)
            } else {
                Triple(null, null, null)
            }
        } catch (e: Exception) {
            Triple(null, null, null)
        }
    }

    private fun formatLocationInfo(info: LocationInfo): String {
        return buildString {
            appendLine("Location:")
            appendLine("  Latitude:  ${info.latitude}")
            appendLine("  Longitude: ${info.longitude}")
            info.address?.let { appendLine("  Address:   $it") }
            info.city?.let { appendLine("  City:      $it") }
            info.country?.let { appendLine("  Country:   $it") }
        }.trimEnd()
    }
}
