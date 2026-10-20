package app.pulse.client.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Russian date/number formatting in the compact style of mobile social apps. */
object Format {
    private val ru = Locale("ru")
    // ThreadLocal.withInitial is API 26+, minSdk is 21.
    private fun fmt(pattern: String) = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat(pattern, ru)
    }
    private val time = fmt("HH:mm")
    private val dayMonth = fmt("d MMM")
    private val full = fmt("d MMM yyyy")

    /** "12:30", "вчера", "5 мар", "5 мар 2023" for a unix-seconds timestamp. */
    fun shortDate(unix: Long): String {
        if (unix <= 0) return ""
        val d = Date(unix * 1000)
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { time = d }
        return when {
            sameDay(now, c) -> time.get()!!.format(d)
            isYesterday(now, c) -> "вчера"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> dayMonth.get()!!.format(d).trimEnd('.')
            else -> full.get()!!.format(d)
        }
    }

    /** "сегодня в 12:30", "вчера в 9:05", "5 мар в 10:00". */
    fun postDate(unix: Long): String {
        if (unix <= 0) return ""
        val d = Date(unix * 1000)
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { time = d }
        val t = time.get()!!.format(d)
        return when {
            sameDay(now, c) -> "сегодня в $t"
            isYesterday(now, c) -> "вчера в $t"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> dayMonth.get()!!.format(d).trimEnd('.') + " в $t"
            else -> full.get()!!.format(d)
        }
    }

    fun time(unix: Long): String = time.get()!!.format(Date(unix * 1000))

    fun dayHeader(unix: Long): String {
        val d = Date(unix * 1000)
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { time = d }
        return when {
            sameDay(now, c) -> "Сегодня"
            isYesterday(now, c) -> "Вчера"
            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) -> SimpleDateFormat("d MMMM", ru).format(d)
            else -> SimpleDateFormat("d MMMM yyyy", ru).format(d)
        }
    }

    fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a * 1000 }
        val cb = Calendar.getInstance().apply { timeInMillis = b * 1000 }
        return sameDay(ca, cb)
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, c: Calendar): Boolean {
        val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(y, c)
    }

    /** 999, 1,2K, 15K, 1,3M */
    fun count(n: Int): String = when {
        n < 1000 -> n.toString()
        n < 10_000 -> String.format(ru, "%.1fK", n / 1000.0).replace(".0K", "K")
        n < 1_000_000 -> "${n / 1000}K"
        else -> String.format(ru, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    }

    fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(ru, "%d:%02d:%02d", h, m, s) else String.format(ru, "%d:%02d", m, s)
    }

    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> String.format(ru, "%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    /** "в сети", "был(а) в 12:30", "был(а) вчера" */
    fun lastSeen(online: Boolean, lastSeen: Long): String = when {
        online -> "в сети"
        lastSeen <= 0 -> ""
        else -> "был(а) " + postDate(lastSeen)
    }

    /** Russian plural: plural(5, "друг", "друга", "друзей") */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val m10 = n % 10
        val m100 = n % 100
        val w = when {
            m10 == 1 && m100 != 11 -> one
            m10 in 2..4 && m100 !in 12..14 -> few
            else -> many
        }
        return "${count(n)} $w"
    }
}
