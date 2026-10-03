package com.voicealert.app

import android.content.Context
import java.io.File

data class Ev(val id: String, val title: String, val def: String, val on: Boolean = true)

object Bn {
    fun d(s: String): String = s.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")
    fun d(n: Int): String = d(n.toString())
}

object Events {
    val alerts = listOf(
        Ev("b20", "🔋 চার্জ ২০% এর নিচে", "ওহ! আপনার ফোনের চার্জ এখন {level} শতাংশ। দয়া করে চার্জারটা লাগিয়ে নিন, নাহলে আমি ঘুমিয়ে পড়ব।"),
        Ev("b10", "🪫 চার্জ ১০% এর নিচে", "সাবধান! চার্জ মাত্র {level} শতাংশ বাকি। প্লিজ, এক্ষুনি চার্জে লাগান।"),
        Ev("b5", "🚨 চার্জ ৫% এর নিচে", "জরুরি! মাত্র {level} শতাংশ চার্জ আছে। এখনই চার্জ না দিলে আমি বন্ধ হয়ে যাব।"),
        Ev("in", "📞 কল আসলে", "আপনার একটি কল আসছে। ফোনটা ধরুন না!"),
        Ev("miss", "📵 মিসড কল হলে", "আপনার একটি মিসড কল আছে। একবার দেখে নিন, কেউ হয়তো আপনাকে খুঁজছে।"),
        Ev("off", "⏻ ফোন বন্ধ হওয়ার সময়", "ফোন বন্ধ হচ্ছে। আবার দেখা হবে, নিজের খেয়াল রাখবেন।"),
        Ev("on", "✨ ফোন চালু হওয়ার সময়", "স্বাগতম! আপনার ফোন চালু হয়েছে। আপনার দিনটি সুন্দর হোক।"),
        Ev("plug", "🔌 চার্জার লাগালে", "চার্জার লাগানো হয়েছে, ধন্যবাদ।"),
        Ev("unplug", "🔕 চার্জার খুললে", "চার্জার খুলে ফেলা হয়েছে।"),
        Ev("full", "✅ ১০০% চার্জ হলে", "আপনার ফোন পুরোপুরি চার্জ হয়েছে। এবার চার্জারটা খুলে নিতে পারেন।"),
        Ev("unlock", "🔓 ফোন আনলক করলে", "স্বাগতম, আপনাকে আবার দেখে ভালো লাগছে।", on = false)
    )

    val adhan = Ev("adhan", "🕌 আজানের অডিও (সব ওয়াক্তের জন্য)", "")

    val prayers = listOf(
        Ev("p_fajr", "🌅 ফজর", "ফজরের নামাজের সময় হয়েছে। উঠুন, অজু করে নামাজে দাঁড়ান।"),
        Ev("p_dhuhr", "☀️ যোহর", "যোহরের নামাজের সময় হয়েছে। চলুন, নামাজের জন্য প্রস্তুত হই।"),
        Ev("p_asr", "🌤 আসর", "আসরের নামাজের সময় হয়েছে। ব্যস্ততার মাঝেও নামাজটা আদায় করে নিন।"),
        Ev("p_maghrib", "🌇 মাগরিব", "মাগরিবের নামাজের সময় হয়েছে। নামাজের জন্য প্রস্তুত হোন।"),
        Ev("p_isha", "🌙 এশা", "এশার নামাজের সময় হয়েছে। ঘুমানোর আগে নামাজটা পড়ে নিন।")
    )

    val extras = listOf(
        Ev("pre", "⏰ ওয়াক্তের আগে রিমাইন্ডার", "{name} নামাজের আর {min} মিনিট বাকি। অজু করে প্রস্তুত হয়ে নিন।"),
        Ev("jumah", "🕌 জুমার দিনের রিমাইন্ডার", "আজ জুমার দিন। গোসল সেরে সুন্দর পোশাকে মসজিদে যাওয়ার প্রস্তুতি নিন।"),
        Ev("sehri", "🍽 সেহরি (রমজান)", "সেহরির সময় শেষ হতে আর ৩০ মিনিট বাকি। এখনই সেহরি সেরে নিন।", on = false),
        Ev("iftar", "🌴 ইফতার (রমজান)", "ইফতারের আর ১০ মিনিট বাকি। দোয়া ও ইফতারের প্রস্তুতি নিন।", on = false),
        Ev("dhikr_m", "📿 সকালের জিকির", "সকালের জিকিরের সময় হয়েছে। কিছুক্ষণ আল্লাহর স্মরণে কাটান।", on = false),
        Ev("dhikr_e", "📿 সন্ধ্যার জিকির", "সন্ধ্যার জিকিরের সময় হয়েছে। কিছুক্ষণ আল্লাহর স্মরণে কাটান।", on = false)
    )

    val islamic = prayers + extras
    fun get(id: String): Ev = (alerts + islamic + adhan).first { it.id == id }
}

class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("va", Context.MODE_PRIVATE)

    fun enabled(e: Ev) = p.getBoolean("en_${e.id}", e.on)
    fun setEnabled(e: Ev, v: Boolean) = p.edit().putBoolean("en_${e.id}", v).apply()

    fun text(e: Ev): String = p.getString("tx_${e.id}", null) ?: e.def
    fun setText(e: Ev, v: String) = p.edit().putString("tx_${e.id}", v).apply()

    fun audio(e: Ev): String? = p.getString("au_${e.id}", null)?.takeIf { File(it).exists() }
    fun setAudio(e: Ev, path: String?) {
        val ed = p.edit()
        if (path == null) ed.remove("au_${e.id}") else ed.putString("au_${e.id}", path)
        ed.apply()
    }

    fun int(k: String, d: Int) = p.getInt(k, d)
    fun setInt(k: String, v: Int) = p.edit().putInt(k, v).apply()

    var pitch: Int
        get() = p.getInt("pitch", 115)
        set(v) = p.edit().putInt("pitch", v).apply()

    var rate: Int
        get() = p.getInt("rate", 95)
        set(v) = p.edit().putInt("rate", v).apply()

    var service: Boolean
        get() = p.getBoolean("svc", true)
        set(v) = p.edit().putBoolean("svc", v).apply()

    var hanafi: Boolean
        get() = p.getBoolean("hanafi", true)
        set(v) = p.edit().putBoolean("hanafi", v).apply()

    var lat: Double
        get() = (p.getString("lat", null) ?: "23.8103").toDouble()
        set(v) = p.edit().putString("lat", v.toString()).apply()

    var lng: Double
        get() = (p.getString("lng", null) ?: "90.4125").toDouble()
        set(v) = p.edit().putString("lng", v.toString()).apply()

    var place: String
        get() = p.getString("place", null) ?: "ঢাকা"
        set(v) = p.edit().putString("place", v).apply()
}
