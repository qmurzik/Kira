package app.pulse.client

import app.pulse.client.core.util.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun pluralRussian() {
        assertEquals("1 друг", Format.plural(1, "друг", "друга", "друзей"))
        assertEquals("3 друга", Format.plural(3, "друг", "друга", "друзей"))
        assertEquals("5 друзей", Format.plural(5, "друг", "друга", "друзей"))
        assertEquals("11 друзей", Format.plural(11, "друг", "друга", "друзей"))
        assertEquals("21 друг", Format.plural(21, "друг", "друга", "друзей"))
        assertEquals("112 друзей", Format.plural(112, "друг", "друга", "друзей"))
    }

    @Test
    fun durations() {
        assertEquals("0:05", Format.duration(5))
        assertEquals("1:05", Format.duration(65))
        assertEquals("1:01:01", Format.duration(3661))
    }

    @Test
    fun compactCounts() {
        assertEquals("999", Format.count(999))
        assertEquals("15K", Format.count(15_000))
        assertEquals("1,2K", Format.count(1_200))
    }
}
