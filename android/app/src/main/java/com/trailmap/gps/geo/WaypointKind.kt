package com.trailmap.gps.geo

import com.trailmap.gps.data.Waypoint

enum class WaypointKind(val label: String) {
    TRAILHEAD("Trailhead"),
    PARKING("Parking"),
    CAMP("Camp"),
    WATER("Water"),
    PASS("Pass"),
    SADDLE("Saddle"),
    SUMMIT("Summit"),
    COL("Col"),
    RIDGE("Ridge"),
    TECHNICAL("Technical"),
    SCRAMBLE("Scramble"),
    SNOW("Snow"),
    GLACIER("Glacier"),
    HAZARD("Hazard"),
    BAILOUT("Bailout"),
    TURNAROUND("Turnaround"),
    DESCENT("Descent"),
    ALTERNATE("Alternate"),
    CUSTOM("Custom");

    val isObjective: Boolean
        get() = this == SUMMIT || this == PASS || this == CAMP || this == TURNAROUND || this == BAILOUT

    companion object {
        fun from(name: String, type: String = ""): WaypointKind {
            val text = "$type $name".lowercase()
            return when {
                text.contains("trailhead") || text.contains("th ") || text.endsWith(" th") -> TRAILHEAD
                text.contains("parking") || text.contains("trailhead parking") -> PARKING
                text.contains("camp") || text.contains("bivy") -> CAMP
                text.contains("water") || text.contains("creek") || text.contains("lake") -> WATER
                text.contains("saddle") -> SADDLE
                text.contains("col") -> COL
                text.contains("pass") -> PASS
                text.contains("summit") || text.contains("peak") || text.contains("top") -> SUMMIT
                text.contains("ridge") -> RIDGE
                text.contains("scramble") -> SCRAMBLE
                text.contains("glacier") -> GLACIER
                text.contains("snow") -> SNOW
                text.contains("technical") || text.contains("crux") -> TECHNICAL
                text.contains("hazard") || text.contains("danger") -> HAZARD
                text.contains("bail") || text.contains("escape") -> BAILOUT
                text.contains("turnaround") || text.contains("turn around") -> TURNAROUND
                text.contains("descent") -> DESCENT
                text.contains("alternate") || text.contains("alt route") -> ALTERNATE
                else -> entries.firstOrNull { it.label.equals(type, true) } ?: CUSTOM
            }
        }
    }
}

fun Waypoint.kind(): WaypointKind = WaypointKind.from(name, type)
