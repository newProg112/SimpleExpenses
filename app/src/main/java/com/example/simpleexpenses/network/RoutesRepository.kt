package com.example.simpleexpenses.network

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimal LatLng + interface so we can swap impls later. */
data class LatLng(val lat: Double, val lng: Double)

interface RoutesRepository {
    /** Returns distance in meters between two points. For Google, this will be *driving* distance. */
    suspend fun computeDistanceMeters(origin: LatLng, dest: LatLng): Int
}

/** v1: offline, free, “as the crow flies” (haversine). */
class CrowFliesRoutesRepository : RoutesRepository {
    override suspend fun computeDistanceMeters(origin: LatLng, dest: LatLng): Int {
        val R = 6371000.0
        val dLat = Math.toRadians(dest.lat - origin.lat)
        val dLon = Math.toRadians(dest.lng - origin.lng)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(origin.lat)) * cos(Math.toRadians(dest.lat)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (R * c).toInt()
    }
}

/**
 * TODO(v2): Google Routes API (Compute Routes).
 * - Read API key from local props / encrypted prefs.
 * - Call REST endpoint, parse legs[].distanceMeters (or distance.value).
 * - Handle errors + fallback to CrowFlies.
 */
class GoogleRoutesRepository /* : RoutesRepository */ {
    // suspend fun computeDistanceMeters(origin: LatLng, dest: LatLng): Int = TODO()
}