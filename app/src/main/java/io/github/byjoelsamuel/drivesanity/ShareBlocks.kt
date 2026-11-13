package io.github.byjoelsamuel.drivesanity

/**
 * What can go on a share picture, sorted into genres (the panel the block lives in) and the blocks
 * within each genre. Blocks of one genre are drawn together in one panel, so a picture reads as a few
 * tidy groups rather than a scatter of numbers.
 */
enum class Genre(val label: String) {
    DETAILS("Details"),
    TRIP("Trip"),
    ROUTE("Route"),
    SPEED("Speed"),
    PERFORMANCE("Performance"),
    ENGINE("Engine"),
    STANDING("Standing start"),
}

enum class Block(val genre: Genre, val label: String) {
    TITLE(Genre.DETAILS, "Title"),
    CAR(Genre.DETAILS, "Car"),
    DATE(Genre.DETAILS, "Date"),

    DISTANCE(Genre.TRIP, "Distance"),
    TIME(Genre.TRIP, "Time"),
    DRIVES(Genre.TRIP, "Drives"),

    PATH(Genre.ROUTE, "Path"),

    TOP_SPEED(Genre.SPEED, "Top speed"),
    AVG_SPEED(Genre.SPEED, "Average"),

    BRAKING(Genre.PERFORMANCE, "Braking"),
    ACCEL(Genre.PERFORMANCE, "Acceleration"),
    CORNERING(Genre.PERFORMANCE, "Cornering"),

    REVS(Genre.ENGINE, "Highest revs"),

    ZERO_60(Genre.STANDING, "0–60 mph"),
    ZERO_100(Genre.STANDING, "0–100 km/h"),
    ;

    companion object {
        fun of(genre: Genre) = entries.filter { it.genre == genre }
    }
}

enum class PanelStyle(val label: String) { GLASS("Glass"), SOLID("Solid"), NONE("None") }

enum class Place(val label: String) { TOP("Top"), MIDDLE("Middle"), BOTTOM("Bottom") }

/** How a picture is put together: which blocks, how dark the photo is behind it, the panel look and where it sits. */
data class ShareConfig(
    val blocks: Set<Block> = Block.entries.toSet(),
    val dim: Float = 0.5f,
    val panel: PanelStyle = PanelStyle.GLASS,
    val place: Place = Place.MIDDLE,
) {
    fun with(block: Block, on: Boolean) = copy(blocks = if (on) blocks + block else blocks - block)

    fun withGenre(genre: Genre, on: Boolean) = copy(blocks = if (on) blocks + Block.of(genre) else blocks - Block.of(genre).toSet())

    /** For keeping between visits: "BLOCK,BLOCK;dim;PANEL;PLACE". */
    fun encode(): String = "${blocks.joinToString(",") { it.name }};$dim;${panel.name};${place.name}"

    companion object {
        fun decode(s: String?): ShareConfig {
            val p = s?.split(';') ?: return ShareConfig()
            if (p.size < 4) return ShareConfig()
            return runCatching {
                ShareConfig(
                    blocks = p[0].split(',').mapNotNull { n -> Block.entries.firstOrNull { it.name == n } }.toSet(),
                    dim = p[1].toFloat().coerceIn(0f, 0.9f),
                    panel = PanelStyle.valueOf(p[2]),
                    place = Place.valueOf(p[3]),
                )
            }.getOrDefault(ShareConfig())
        }
    }
}

/** Everything a picture could show, and what of it there is. A null means there's none of that (so its block is left out). */
class CardData(
    val title: String,
    val whenText: String,
    val car: String,
    val units: Units,
    val distanceM: Double? = null,
    val durationS: Double? = null,
    val drives: Int? = null,
    val topKmh: Float? = null,
    val avgKmh: Float? = null,
    val maxRpm: Float? = null,
    val brakeG: Float? = null,
    val accelG: Float? = null,
    val latG: Float? = null,
    val path: List<SessionPoint> = emptyList(),
    val pathHidden: Boolean = false,
    /** A 0–60 run: the time to 60 mph and to 100 km/h, in milliseconds. */
    val to60Ms: Long? = null,
    val to100Ms: Long? = null,
) {
    /** Whether there's something for [b] to show. */
    fun has(b: Block): Boolean = when (b) {
        Block.TITLE, Block.CAR, Block.DATE -> true
        Block.DISTANCE -> distanceM != null
        Block.TIME -> durationS != null
        Block.DRIVES -> drives != null
        Block.PATH -> path.size > 1 || pathHidden
        Block.TOP_SPEED -> topKmh != null && topKmh > 0f
        Block.AVG_SPEED -> avgKmh != null && avgKmh > 0f
        Block.BRAKING -> brakeG != null
        Block.ACCEL -> accelG != null
        Block.CORNERING -> latG != null
        Block.REVS -> maxRpm != null && maxRpm > 0f
        Block.ZERO_60 -> to60Ms != null
        Block.ZERO_100 -> to100Ms != null
    }

    companion object {
        fun ofSession(session: Session, title: String, whenText: String, car: String, units: Units, hideEnds: Boolean): CardData {
            val path = if (hideEnds) session.trimmedPath(ShareCard.HIDE_ENDS_M) else session.path
            return CardData(
                title = title, whenText = whenText, car = car, units = units,
                distanceM = session.distanceM, durationS = session.durationS,
                topKmh = session.topKmh, avgKmh = session.avgMovingKmh, maxRpm = session.maxRpm,
                brakeG = session.peakBrakeG, accelG = session.peakAccelG, latG = session.peakLatG,
                path = path, pathHidden = hideEnds && path.size < 2 && session.path.size > 1,
            )
        }

        /** All the recordings added up: the profile. */
        fun ofProfile(sessions: List<Session>, car: String, units: Units, whenText: String): CardData {
            val real = sessions.filter { it.points.isNotEmpty() }
            val totalS = real.sumOf { it.durationS }
            val dist = real.sumOf { it.distanceM }
            return CardData(
                title = "My drives", whenText = whenText, car = car, units = units,
                distanceM = dist, durationS = totalS, drives = real.size,
                topKmh = real.maxOfOrNull { it.topKmh }, avgKmh = if (totalS > 0) (dist / totalS * 3.6).toFloat() else null,
                maxRpm = real.maxOfOrNull { it.maxRpm },
                brakeG = real.maxOfOrNull { it.peakBrakeG }, accelG = real.maxOfOrNull { it.peakAccelG }, latG = real.maxOfOrNull { it.peakLatG },
            )
        }

        fun ofRun(to60Ms: Long?, to100Ms: Long?, whenText: String, car: String, units: Units) =
            CardData(title = "0–60", whenText = whenText, car = car, units = units, to60Ms = to60Ms, to100Ms = to100Ms)
    }
}
