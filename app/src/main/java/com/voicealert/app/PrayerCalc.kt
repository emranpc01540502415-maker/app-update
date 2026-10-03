package com.voicealert.app

import kotlin.math.*

/** অফলাইন নামাজের সময় গণনা (করাচি পদ্ধতি: ফজর/এশা ১৮°)। */
object PrayerCalc {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fix(a: Double, b: Double) = a - b * floor(a / b)

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    private class Sun(val decl: Double, val eqt: Double)

    private fun sun(jd: Double): Sun {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0, 24.0)
        return Sun(deg(asin(sin(rad(e)) * sin(rad(l)))), q / 15.0 - ra)
    }

    /** ফলাফল: স্থানীয় সময়ে ঘণ্টা (যেমন 17.5 = 5:30 PM)। */
    fun compute(lat: Double, lng: Double, y: Int, m: Int, d: Int, tz: Double, hanafi: Boolean): Map<String, Double> {
        val jd = julian(y, m, d) - lng / 360.0
        fun mid(t: Double) = fix(12.0 - sun(jd + t).eqt, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val dec = sun(jd + t).decl
            val x = (-sin(rad(angle)) - sin(rad(dec)) * sin(rad(lat))) / (cos(rad(dec)) * cos(rad(lat)))
            val h = deg(acos(x.coerceIn(-1.0, 1.0))) / 15.0
            return mid(t) + if (ccw) -h else h
        }
        fun asrTime(t: Double): Double {
            val dec = sun(jd + t).decl
            val factor = if (hanafi) 2.0 else 1.0
            val angle = -deg(atan(1.0 / (factor + tan(rad(abs(lat - dec))))))
            return angleTime(angle, t, false)
        }
        var fajr = 5.0; var rise = 6.0; var noon = 12.0; var asr = 13.0; var sunset = 18.0; var isha = 18.0
        repeat(2) {
            fajr = angleTime(18.0, fajr / 24, true)
            rise = angleTime(0.833, rise / 24, true)
            noon = mid(noon / 24)
            asr = asrTime(asr / 24)
            sunset = angleTime(0.833, sunset / 24, false)
            isha = angleTime(18.0, isha / 24, false)
        }
        val adj = tz - lng / 15.0
        return linkedMapOf(
            "fajr" to fajr + adj, "sunrise" to rise + adj, "dhuhr" to noon + adj,
            "asr" to asr + adj, "maghrib" to sunset + adj, "isha" to isha + adj
        )
    }

    /** কিবলার দিক (উত্তর থেকে ঘড়ির কাঁটার দিকে, ডিগ্রি)। */
    fun qibla(lat: Double, lng: Double): Double {
        val kLat = rad(21.4225); val kLng = rad(39.8262)
        val p1 = rad(lat); val dl = kLng - rad(lng)
        val y = sin(dl)
        val x = cos(p1) * tan(kLat) - sin(p1) * cos(dl)
        return fix(deg(atan2(y, x)), 360.0)
    }
}
