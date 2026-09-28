package com.trailmap.gps.conditions

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecInfoParserTest {
    @Test
    fun stripHtmlRemovesTagsAndEntities() {
        val clean = RecInfoParser.stripHtml("<p>Upper Pines&nbsp;&amp; tents</p><br/>Open")
        assertEquals("Upper Pines & tents Open", clean)
    }

    @Test
    fun campTypeDetectsCampgroundsOnly() {
        assertTrue(RecInfoParser.isCampFacility("Campground"))
        assertTrue(RecInfoParser.isCampFacility("Camping"))
        assertFalse(RecInfoParser.isCampFacility("Visitor Center"))
        assertFalse(RecInfoParser.isCampFacility("Boat Ramp"))
    }

    @Test
    fun parksAndAlertsJoinOnParkCode() {
        val parks = RecInfoParser.parseNpsParks(
            JSONObject(
                """{"data":[{"parkCode":"yose","fullName":"Yosemite National Park","latitude":"37.7","longitude":"-119.6","states":"CA","url":"https://www.nps.gov/yose"}]}"""
            ),
            37.75,
            -119.58
        )
        assertEquals("yose", parks.single().parkCode)
        assertTrue(parks.single().distanceMiles!! < 10.0)
        val alerts = RecInfoParser.parseNpsAlerts(
            JSONObject(
                """{"data":[{"title":"Tioga closed","category":"Park Closure","parkCode":"yose","description":"<p>Snow</p>","url":"https://www.nps.gov/yose/planyourvisit/conditions.htm"}]}"""
            ),
            parks.associate { it.parkCode to it.name }
        )
        assertEquals("Yosemite National Park", alerts.single().parkName)
        assertEquals("Snow", alerts.single().description)
        assertEquals("Park Closure", alerts.single().category)
    }

    @Test
    fun campgroundsOutsideRadiusAreDropped() {
        val json = JSONObject(
            """{"data":[
              {"name":"Near Camp","parkCode":"yose","latitude":"37.75","longitude":"-119.58","description":"Walk-in","url":"https://nps.gov/near"},
              {"name":"Far Camp","parkCode":"yose","latitude":"40.0","longitude":"-120.0","description":"Too far","url":"https://nps.gov/far"}
            ]}"""
        )
        val camps = RecInfoParser.parseNpsCampgrounds(json, 37.75, -119.58, mapOf("yose" to "Yosemite"), 40.0)
        assertEquals(listOf("Near Camp"), camps.map { it.name })
        assertEquals("NPS", camps.single().source)
    }

    @Test
    fun ridbFacilitiesParseAndSortByDistance() {
        val json = JSONObject(
            """{"RECDATA":[
              {"FacilityName":"Far Ramp","FacilityTypeDescription":"Boat Ramp","FacilityLatitude":38.2,"FacilityLongitude":-119.2,"FacilityDescription":"<b>Ramp</b>","FacilityID":"2"},
              {"FacilityName":"Close Camp","FacilityTypeDescription":"Campground","FacilityLatitude":37.76,"FacilityLongitude":-119.59,"FacilityDescription":"Sites","FacilityID":"1","Reservable":true}
            ]}"""
        )
        val places = RecInfoParser.parseRidbFacilities(json, 37.75, -119.58)
        assertEquals("Close Camp", places.first().name)
        assertTrue(places.first().reservable)
        assertEquals("RIDB", places.first().source)
        assertEquals("Ramp", places.last().summary)
    }
}
