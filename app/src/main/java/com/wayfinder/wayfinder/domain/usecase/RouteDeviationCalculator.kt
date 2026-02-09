package com.wayfinder.wayfinder.domain.usecase

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.wayfinder.wayfinder.core.NavigationConstants
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Calculates route deviation using on-device polyline checks.
 * This approach minimizes API calls by checking locally if user is off-route.
 * 
 * Uses Google Maps Utils PolyUtil for accurate polyline distance calculation:
 * - PolyUtil.isLocationOnPath() for simple on/off check
 * - Custom perpendicular distance calculation for precise deviation metrics
 * 
 * API calls only occur when:
 * 1. User is confirmed off-route (>30m from path)
 * 2. Off-route condition persists for >10 seconds (debounce)
 * 3. Minimum 30 seconds since last reroute request
 */
class RouteDeviationCalculator {
    
    /**
     * Result of deviation calculation.
     */
    data class DeviationResult(
        /** Whether user is considered off-route. */
        val isOffRoute: Boolean,
        
        /** Perpendicular distance from route in meters. */
        val deviationMeters: Double,
        
        /** Index of closest segment on the route. */
        val closestSegmentIndex: Int,
        
        /** Projected point on route closest to user. */
        val closestPointOnRoute: LatLng?
    )
    
    /**
     * Calculate deviation from route using on-device polyline analysis.
     * 
     * @param userLocation Current user location.
     * @param routePolyline List of LatLng points defining the route.
     * @param toleranceMeters Distance threshold to consider on-route (default: 30m).
     * @return DeviationResult with off-route status and metrics.
     */
    fun calculateDeviation(
        userLocation: LatLng,
        routePolyline: List<LatLng>,
        toleranceMeters: Double = NavigationConstants.OFF_ROUTE_THRESHOLD_METERS
    ): DeviationResult {
        if (routePolyline.size < 2) {
            return DeviationResult(
                isOffRoute = false,
                deviationMeters = 0.0,
                closestSegmentIndex = 0,
                closestPointOnRoute = null
            )
        }
        
        // Quick check using PolyUtil (very fast)
        val isOnPath = PolyUtil.isLocationOnPath(
            userLocation,
            routePolyline,
            true, // geodesic
            NavigationConstants.ON_PATH_TOLERANCE_METERS // tolerance in meters
        )
        
        if (isOnPath) {
            // User is on path within tolerance
            return DeviationResult(
                isOffRoute = false,
                deviationMeters = 0.0,
                closestSegmentIndex = findClosestSegmentIndex(userLocation, routePolyline),
                closestPointOnRoute = userLocation
            )
        }
        
        // User might be off-path, calculate precise deviation
        val (closestSegmentIndex, closestPoint) = findClosestPointOnPolyline(userLocation, routePolyline)
        val deviationMeters = SphericalUtil.computeDistanceBetween(userLocation, closestPoint)
        
        return DeviationResult(
            isOffRoute = deviationMeters > toleranceMeters,
            deviationMeters = deviationMeters,
            closestSegmentIndex = closestSegmentIndex,
            closestPointOnRoute = closestPoint
        )
    }
    
    /**
     * Find the index of the closest segment on the polyline.
     */
    private fun findClosestSegmentIndex(
        point: LatLng,
        polyline: List<LatLng>
    ): Int {
        var minDistance = Double.MAX_VALUE
        var closestIndex = 0
        
        for (i in 0 until polyline.size - 1) {
            val segmentStart = polyline[i]
            val segmentEnd = polyline[i + 1]
            
            val distance = distanceToSegment(point, segmentStart, segmentEnd)
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
            }
        }
        
        return closestIndex
    }
    
    /**
     * Find the closest point on the polyline and its segment index.
     */
    private fun findClosestPointOnPolyline(
        point: LatLng,
        polyline: List<LatLng>
    ): Pair<Int, LatLng> {
        var minDistance = Double.MAX_VALUE
        var closestIndex = 0
        var closestPoint = polyline[0]
        
        for (i in 0 until polyline.size - 1) {
            val segmentStart = polyline[i]
            val segmentEnd = polyline[i + 1]
            
            val projection = projectPointOnSegment(point, segmentStart, segmentEnd)
            val distance = SphericalUtil.computeDistanceBetween(point, projection)
            
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = i
                closestPoint = projection
            }
        }
        
        return Pair(closestIndex, closestPoint)
    }
    
    /**
     * Project a point onto a line segment.
     */
    private fun projectPointOnSegment(
        point: LatLng,
        segmentStart: LatLng,
        segmentEnd: LatLng
    ): LatLng {
        // Vector from start to end
        val dx = segmentEnd.longitude - segmentStart.longitude
        val dy = segmentEnd.latitude - segmentStart.latitude
        
        // Vector from start to point
        val px = point.longitude - segmentStart.longitude
        val py = point.latitude - segmentStart.latitude
        
        // Project point onto line
        val segmentLengthSq = dx * dx + dy * dy
        if (segmentLengthSq == 0.0) {
            return segmentStart
        }
        
        var t = (px * dx + py * dy) / segmentLengthSq
        t = t.coerceIn(0.0, 1.0) // Clamp to segment
        
        return LatLng(
            segmentStart.latitude + t * dy,
            segmentStart.longitude + t * dx
        )
    }
    
    /**
     * Calculate distance from point to line segment in meters.
     */
    private fun distanceToSegment(
        point: LatLng,
        segmentStart: LatLng,
        segmentEnd: LatLng
    ): Double {
        val projection = projectPointOnSegment(point, segmentStart, segmentEnd)
        return SphericalUtil.computeDistanceBetween(point, projection)
    }
    
    /**
     * Calculate the progress along the route (0.0 to 1.0).
     */
    fun calculateRouteProgress(
        userLocation: LatLng,
        routePolyline: List<LatLng>
    ): Double {
        if (routePolyline.size < 2) return 0.0
        
        val (closestSegmentIndex, _) = findClosestPointOnPolyline(userLocation, routePolyline)
        
        // Calculate distance traveled
        var distanceTraveled = 0.0
        for (i in 0 until closestSegmentIndex) {
            distanceTraveled += SphericalUtil.computeDistanceBetween(
                routePolyline[i],
                routePolyline[i + 1]
            )
        }
        
        // Calculate total route distance
        var totalDistance = 0.0
        for (i in 0 until routePolyline.size - 1) {
            totalDistance += SphericalUtil.computeDistanceBetween(
                routePolyline[i],
                routePolyline[i + 1]
            )
        }
        
        return if (totalDistance > 0) distanceTraveled / totalDistance else 0.0
    }
    
    /**
     * Calculate remaining distance on the route in meters.
     */
    fun calculateRemainingDistance(
        userLocation: LatLng,
        routePolyline: List<LatLng>
    ): Double {
        if (routePolyline.size < 2) return 0.0
        
        val (closestSegmentIndex, closestPoint) = findClosestPointOnPolyline(userLocation, routePolyline)
        
        // Distance from closest point to end of current segment
        var remainingDistance = SphericalUtil.computeDistanceBetween(
            closestPoint,
            routePolyline[closestSegmentIndex + 1]
        )
        
        // Add distance for remaining segments
        for (i in (closestSegmentIndex + 1) until (routePolyline.size - 1)) {
            remainingDistance += SphericalUtil.computeDistanceBetween(
                routePolyline[i],
                routePolyline[i + 1]
            )
        }
        
        return remainingDistance
    }
}
