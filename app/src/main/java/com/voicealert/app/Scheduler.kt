package com.voicealert.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Scheduler {
    class Trig(var at: Long, val id: String, val name: String = "", val min: Int = -1)

    val NAMES = listOf("fajr" to "ফজর", "dhuhr" to "যোহর", "asr" to "আসর", "maghrib" to "মাগরিব", "isha" to "এশা")
    private const val MIN = 60_000L

    fun times(ctx: Context, dayOffset: Int): Map<String, Long> {
        val p = Prefs(ctx)
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        val r = PrayerCalc.compute(
            p.lat, p.lng, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH), tz, p.hanafi
        )
        val adj = p.int("adj", 0) * MIN
        return r.mapValues { (k, h) -> cal.timeInMillis + (h * 3_600_000).toLong() + (if (k == "sunrise") 0L else adj) }
    }

    fun fmt(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        val h = c.get(Calendar.HOUR_OF_DAY)
        val h12 = if (h % 12 == 0) 12 else h % 12
        val per = when (h) { in 4..5 -> "ভোর"; in 6..11 -> "সকাল"; in 12..15 -> "দুপুর"; in 16..17 -> "বিকাল"; in 18..19 -> "সন্ধ্যা"; else -> "রাত" }
        return "$per ${Bn.d(h12)}:${Bn.d(String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)))}"
    }

    fun nextPrayer(ctx: Context): Pair<String, Long>? {
        val now = System.currentTimeMillis()
        for (off in 0..1) {
            val t = times(ctx, off)
            for ((k, n) in NAMES) { val at = t[k] ?: continue; if (at > now) return n to at }
        }
        return null
    }

    fun triggers(ctx: Context): List<Trig> {
        val p = Prefs(ctx)
        val out = mutableListOf<Trig>()
        val pre = p.int("pre", 10)
        for (off in 0..1) {
            val t = times(ctx, off)
            val friday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, off) }
                .get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
            for ((k, n) in NAMES) {
                val at = t[k] ?: continue
                if (p.enabled(Events.get("p_$k"))) out += Trig(at, "p_$k", n)
                if (pre > 0 && p.enabled(Events.get("pre"))) out += Trig(at - pre * MIN, "pre", n, pre)
            }
            fun add(id: String, at: Long?) { if (at != null && p.enabled(Events.get(id))) out += Trig(at, id) }
            add("sehri", t["fajr"]?.minus(30 * MIN))
            add("iftar", t["maghrib"]?.minus(10 * MIN))
            add("dhikr_m", t["sunrise"]?.plus(15 * MIN))
            add("dhikr_e", t["maghrib"]?.minus(20 * MIN))
            if (friday) add("jumah", t["dhuhr"]?.minus(60 * MIN))
        }
        val sorted = out.sortedBy { it.at }
        for (i in 1 until sorted.size) if (sorted[i].at <= sorted[i - 1].at) sorted[i].at = sorted[i - 1].at + 4000
        val now = System.currentTimeMillis()
        return sorted.filter { it.at > now + 500 }
    }

    private fun pi(ctx: Context, rc: Int, action: String, fill: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, AlarmReceiver::class.java).setAction(action).apply(fill),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAt(ctx: Context, at: Long, pi: PendingIntent, clockFallback: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else if (clockFallback) {
                val show = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** পরবর্তী রিমাইন্ডারের জন্য এলার্ম বসায়। প্রতিবার ফায়ার হওয়ার পর নিজেই পরেরটা বসায়। */
    fun scheduleNext(ctx: Context) {
        if (!Prefs(ctx).service) { cancelAll(ctx); return }
        val tr = triggers(ctx).firstOrNull()
        val pi = pi(ctx, 1, "va.FIRE") {
            if (tr != null) { putExtra("id", tr.id); putExtra("name", tr.name); putExtra("min", tr.min) }
        }
        if (tr == null) { ctx.getSystemService(AlarmManager::class.java).cancel(pi); return }
        setAt(ctx, tr.at, pi, true)
    }

    /** সেবা মরে গেলে আবার চালু করার জন্য নিয়মিত পাহারাদার এলার্ম। */
    fun keepAlive(ctx: Context, delayMs: Long = 30 * MIN) {
        if (!Prefs(ctx).service) return
        setAt(ctx, System.currentTimeMillis() + delayMs, pi(ctx, 2, "va.KEEP"), false)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pi(ctx, 1, "va.FIRE")); am.cancel(pi(ctx, 2, "va.KEEP"))
    }

    fun startService(ctx: Context, fill: Intent.() -> Unit = {}) {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AlertService::class.java).apply(fill))
        } catch (_: Exception) { /* সিস্টেম অনুমতি দেয়নি; পরের পাহারাদার এলার্মে আবার চেষ্টা হবে */ }
    }
}
