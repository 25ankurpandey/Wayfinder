import com.wayfinder.wayfinder.Polyline

data class DirectionsResult(
    val routes: List<Route>
)

data class Route(
    val legs: List<Leg>,
    val overview_polyline: Polyline
)

data class Leg(
    val distance: Distance,
    val duration: Duration
)

data class Distance(
    val text: String,
    val value: Int // Distance in meters
)

data class Duration(
    val text: String,
    val value: Int // Duration in seconds
)
