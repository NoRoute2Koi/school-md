package koi.schoolmd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import koi.schoolmd.data.Region

class RegionTest {

    @Test
    fun testAllRegionsConfigured() {
        val regions = Region.entries
        assertEquals(6, regions.size)

        for (r in regions) {
            assertTrue(r.title.isNotBlank())
            assertTrue(r.subtitle.isNotBlank())
            assertTrue(r.loginPortalUrl.startsWith("https://"))
            assertTrue(r.tokenUrl.startsWith("https://"))
            assertTrue(r.tokenRefreshUrl.startsWith("https://"))
            assertTrue(r.apiHost.isNotBlank())
            assertTrue(r.tokenRefreshUrl.contains(r.apiHost))
        }
    }

    @Test
    fun testRegionFromName() {
        assertEquals(Region.MOSCOW, Region.fromName("MOSCOW"))
        assertEquals(Region.MOSCOW_REGION, Region.fromName("MOSCOW_REGION"))
        assertEquals(Region.TATARSTAN, Region.fromName("TATARSTAN"))
        assertEquals(Region.TYUMEN, Region.fromName("TYUMEN"))
        assertEquals(Region.KALUGA, Region.fromName("KALUGA"))
        assertEquals(Region.DAGESTAN, Region.fromName("DAGESTAN"))

        // By title
        assertEquals(Region.TATARSTAN, Region.fromName("Татарстан"))
        assertEquals(Region.DAGESTAN, Region.fromName("Дагестан"))

        // By host
        assertEquals(Region.TATARSTAN, Region.fromName("ms-edu.tatar.ru"))
        assertEquals(Region.TYUMEN, Region.fromName("myschool.72to.ru"))

        // Fallback
        assertEquals(Region.MOSCOW_REGION, Region.fromName("UNKNOWN_REGION"))
    }
}
