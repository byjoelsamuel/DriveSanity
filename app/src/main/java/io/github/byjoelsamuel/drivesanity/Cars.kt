package io.github.byjoelsamuel.drivesanity

import kotlin.math.ceil
import kotlin.math.max

/** What the app needs to know about a car: how its gears work and where its red zone starts. */
data class CarProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    /** The gear ratios, when they're known. Null means the app learns them while you drive. */
    val gears: GearTable?,
    /** False for a CVT, which has no fixed gears to show. */
    val hasGears: Boolean,
    /** Where the red zone starts, in rpm. */
    val redline: Float,
    /** The top of the rev scale. */
    val revMax: Float,
    /** True when the redline came from the owner; false for a starting guess to adjust. */
    val redlineChecked: Boolean,
    val note: String,
    /** Petrol engines only: the fuel economy estimate relies on the 14.7 : 1 air to fuel ratio. */
    val petrol: Boolean = true,
) {
    /** The red zone start and the top of the rev scale, using the owner's own redline if they set one. */
    fun revScale(redlineOverride: Float? = null): Pair<Float, Float> {
        val red = redlineOverride ?: redline
        return red to max(revMax, ceil((red + 500f) / 1000f) * 1000f)
    }
}

/**
 * The cars the app knows. Only the Mazda3 has published gear ratios the app could check; for the
 * others it learns the gears by watching speed against RPM while you drive through them, which
 * takes a few minutes. Redlines for those are a starting guess from where each engine makes its
 * peak power, to be set against the tachometer in Setup.
 */
object CarProfiles {
    const val DEFAULT_ID = "mazda3"

    val MAZDA3 = CarProfile(
        id = DEFAULT_ID,
        name = "Mazda3 2.5 AWD (2023)",
        subtitle = "6-speed automatic",
        // Gearbox ratios and the AWD final drive from Mazda's 2023 spec sheet, on 215/45 R18 tyres.
        gears = GearTable.fromGearbox(
            gears = listOf(3.55, 2.02, 1.35, 1.00, 0.74, 0.60),
            finalDrive = 3.626,
            tyreWidthMm = 215, aspect = 45, rimInches = 18,
        ),
        hasGears = true,
        redline = 6300f,
        revMax = 8000f,
        redlineChecked = true,
        note = "Gear ratios and final drive from Mazda's 2023 spec sheet, on 215/45 R18 tyres.",
    )

    val ALCAZAR_PETROL = CarProfile(
        id = "alcazar-petrol",
        name = "Hyundai Alcazar 1.5 turbo petrol",
        subtitle = "6-speed manual or 7-speed DCT",
        gears = null,
        hasGears = true,
        redline = 6000f,
        revMax = 7000f,
        redlineChecked = false,
        note = "Peak power at 5,500 rpm; 215/60 R17 or 215/55 R18 tyres. Hyundai doesn't publish the gear ratios, so the app learns the gears as you drive through them.",
    )

    val ALCAZAR_DIESEL = CarProfile(
        id = "alcazar-diesel",
        name = "Hyundai Alcazar 1.5 diesel",
        subtitle = "6-speed manual or 6-speed automatic",
        gears = null,
        hasGears = true,
        redline = 4800f,
        revMax = 6000f,
        redlineChecked = false,
        note = "Peak power at 4,000 rpm; 215/60 R17 or 215/55 R18 tyres. Hyundai doesn't publish the gear ratios, so the app learns the gears as you drive through them. The fuel economy estimate is for petrol engines only.",
        petrol = false,
    )

    val AMAZE = CarProfile(
        id = "amaze",
        name = "Honda Amaze 1.2 petrol",
        subtitle = "5-speed manual",
        gears = null,
        hasGears = true,
        redline = 6600f,
        revMax = 8000f,
        redlineChecked = false,
        note = "88 hp at 6,000 rpm; about 2,950 rpm at 100 km/h in 5th (Team-BHP's measurement). Honda doesn't publish the gear ratios, so the app learns the gears as you drive through them.",
    )

    val AMAZE_CVT = CarProfile(
        id = "amaze-cvt",
        name = "Honda Amaze 1.2 petrol (CVT)",
        subtitle = "Automatic with no fixed gears",
        gears = null,
        hasGears = false,
        redline = 6600f,
        revMax = 8000f,
        redlineChecked = false,
        note = "A CVT doesn't have fixed gears, so the gear display says CVT.",
    )

    val all = listOf(MAZDA3, ALCAZAR_PETROL, ALCAZAR_DIESEL, AMAZE, AMAZE_CVT)
    val default = MAZDA3

    fun byId(id: String?): CarProfile = all.firstOrNull { it.id == id } ?: default
}
