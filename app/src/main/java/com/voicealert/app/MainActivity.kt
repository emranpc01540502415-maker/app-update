package com.voicealert.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.io.File
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private var pickFor: Ev? = null
    private val labels = mutableMapOf<String, TextView>()
    private lateinit var statusTv: TextView
    private lateinit var timesBox: LinearLayout
    private lateinit var nextTv: TextView
    private lateinit var hijriTv: TextView
    private lateinit var placeTv: TextView
    private lateinit var qiblaTv: TextView
    private lateinit var qv: QiblaView
    private lateinit var alertsBox: LinearLayout
    private lateinit var islamBox: LinearLayout
    private lateinit var tabA: MaterialButton
    private lateinit var tabB: MaterialButton
    private var wantBattery = false
    private var sm: SensorManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { updateNext(); handler.postDelayed(this, 20_000) }
    }

    private val BG = Color.parseColor("#0B1220")
    private val CARD = Color.parseColor("#141E33")
    private val MUTED = Color.parseColor("#9AA7C0")

    private val cities = listOf(
        Triple("ঢাকা", 23.8103, 90.4125), Triple("চট্টগ্রাম", 22.3569, 91.7832),
        Triple("রাজশাহী", 24.3745, 88.6042), Triple("খুলনা", 22.8456, 89.5403),
        Triple("সিলেট", 24.8949, 91.8687), Triple("বরিশাল", 22.7010, 90.3535),
        Triple("রংপুর", 25.7439, 89.2752), Triple("ময়মনসিংহ", 24.7471, 90.4203),
        Triple("কুমিল্লা", 23.4607, 91.1809), Triple("কক্সবাজার", 21.4272, 92.0058)
    )
    private val hijriMonths = arrayOf(
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    )
    private val weekdays = arrayOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val ev = pickFor
        if (uri == null || ev == null) return@registerForActivityResult
        try {
            val f = File(filesDir, "v_${ev.id}")
            contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            prefs.setAudio(ev, f.absolutePath)
            refreshLabel(ev)
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইলটি পড়া যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterPerms() }
    private val locPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) useMyLocation() else Toast.makeText(this, "লোকেশনের অনুমতি দেওয়া হয়নি", Toast.LENGTH_SHORT).show()
    }

    private val sensorListener = object : SensorEventListener {
        private val rm = FloatArray(9)
        private val ori = FloatArray(3)
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rm, e.values)
            SensorManager.getOrientation(rm, ori)
            var az = Math.toDegrees(ori[0].toDouble()).toFloat()
            if (az < 0) az += 360f
            val d = ((az - qv.azimuth + 540f) % 360f) - 180f
            qv.azimuth = (qv.azimuth + d * 0.15f + 360f) % 360f
            qv.invalidate()
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    // ---------- helpers ----------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun bg(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun btn(text: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text; textSize = 12f; isAllCaps = false
            setPadding(dp(4), paddingTop, dp(4), paddingBottom)
            setOnClickListener { onClick() }
        }

    private fun lp(top: Int) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg(CARD, 18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun seek(min: Int, max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max - min; progress = value - min
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) onChange(p + min) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun stepper(
        label: String, suffix: String, key: String, def: Int, lo: Int, hi: Int,
        zeroText: String? = null, changed: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val v = tv("", 14f).apply { gravity = Gravity.CENTER; minWidth = dp(76) }
        fun upd() {
            val x = prefs.int(key, def)
            v.text = if (x == 0 && zeroText != null) zeroText
            else (if (x < 0) "−" else if (x > 0 && lo < 0) "+" else "") + Bn.d(abs(x)) + " " + suffix
        }
        fun step(d: Int) { prefs.setInt(key, (prefs.int(key, def) + d).coerceIn(lo, hi)); upd(); changed() }
        fun small(t: String, d: Int) = btn(t) { step(d) }.apply { minWidth = 0; minimumWidth = dp(44) }
        row.addView(tv(label, 13f, MUTED), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(small("−", -1)); row.addView(v); row.addView(small("+", 1))
        upd()
        return row
    }

    private fun refreshLabel(ev: Ev) {
        labels[ev.id]?.text =
            if (prefs.audio(ev) != null) "🎵 আপনার বাছাই করা ভয়েস/অডিও ব্যবহার হচ্ছে"
            else if (ev.id == "adhan") "ফাইল বাছাই না করলে লেখা থেকে কণ্ঠে রিমাইন্ডার হবে"
            else "🗣 ডিফল্ট সুন্দরী কণ্ঠ (লেখা থেকে)"
    }

    // ---------- UI ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#7C5CFF"), Color.parseColor("#FF5C93"))
            ).apply { cornerRadius = dp(24).toFloat() }
        }
        header.addView(tv("🎙 ভয়েস অ্যালার্ট", 24f, bold = true))
        header.addView(tv("ফোনের প্রতিটি মুহূর্তে আকর্ষণীয় কণ্ঠের রিমাইন্ডার ও নামাজের সময়", 13f, Color.parseColor("#F3EEFF")))
        header.addView(MaterialSwitch(this).apply {
            text = "ভয়েস সেবা চালু (সবসময়)"; setTextColor(Color.WHITE); isChecked = prefs.service
            setPadding(0, dp(12), 0, 0)
            setOnCheckedChangeListener { _, on ->
                prefs.service = on
                if (on) ensure(false)
                else {
                    stopService(Intent(this@MainActivity, AlertService::class.java))
                    Scheduler.cancelAll(this@MainActivity); Voice.stop()
                }
            }
        })
        root.addView(header, lp(0))

        // সেটআপ চেকলিস্ট
        val status = card()
        status.addView(tv("🛡 সবসময় চালু রাখার চেকলিস্ট", 16f, bold = true))
        statusTv = tv("", 13f); statusTv.setLineSpacing(0f, 1.2f)
        status.addView(statusTv, lp(6))
        status.addView(tv("সবগুলো ✅ হলে অ্যাপ বন্ধ থাকলেও এবং ফোন রিস্টার্টের পরেও নিজে থেকে কাজ করবে।", 11f, MUTED), lp(4))
        val sRow = LinearLayout(this)
        sRow.addView(btn("✅ অনুমতি দিন") { ensure(true) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        sRow.addView(btn("🚀 অটো-স্টার্ট") { openAutoStart() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        status.addView(sRow, lp(6))
        root.addView(status, lp(12))

        // ট্যাব
        val tabs = LinearLayout(this)
        tabA = btn("🔊 অ্যালার্ট") { showTab(0) }
        tabB = btn("🕌 ইসলামিক") { showTab(1) }
        tabs.addView(tabA, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        tabs.addView(tabB, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(tabs, lp(12))

        alertsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        islamBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(alertsBox); root.addView(islamBox)
        buildAlerts(); buildIslamic()
        showTab(0)

        if (prefs.service) ensure(false)
    }

    private fun showTab(i: Int) {
        alertsBox.visibility = if (i == 0) View.VISIBLE else View.GONE
        islamBox.visibility = if (i == 1) View.VISIBLE else View.GONE
        tabA.alpha = if (i == 0) 1f else 0.5f
        tabB.alpha = if (i == 1) 1f else 0.5f
    }

    private fun buildAlerts() {
        val tune = card()
        tune.addView(tv("🎚 কণ্ঠ সেটিংস", 16f, bold = true))
        tune.addView(tv("সুর (পিচ) — বেশি হলে মিহি কণ্ঠ", 12f, MUTED))
        tune.addView(seek(50, 200, prefs.pitch) { prefs.pitch = it })
        tune.addView(tv("গতি — কম হলে ধীরে বলবে", 12f, MUTED))
        tune.addView(seek(50, 150, prefs.rate) { prefs.rate = it })
        tune.addView(tv("লেখার মধ্যে {level} = চার্জের শতাংশ, {name} = নামাজের নাম, {min} = মিনিট — বসিয়ে দেওয়া হবে।", 11f, MUTED), lp(6))
        tune.addView(tv("বাংলা কণ্ঠ না পেলে: সেটিংস > ভাষা > টেক্সট-টু-স্পিচ > Google > বাংলা ভয়েস ইনস্টল করুন।", 11f, MUTED), lp(4))
        alertsBox.addView(tune, lp(12))
        Events.alerts.forEach { alertsBox.addView(eventCard(it), lp(12)) }
    }

    private fun buildIslamic() {
        // আজকের সময়
        val t = card()
        hijriTv = tv("", 14f, Color.parseColor("#FFD27C"), true)
        placeTv = tv("", 12f, MUTED)
        nextTv = tv("", 15f, Color.parseColor("#7CFFB2"), true)
        timesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        t.addView(tv("🕌 আজকের নামাজের সময়", 16f, bold = true))
        t.addView(hijriTv, lp(4)); t.addView(placeTv, lp(2)); t.addView(nextTv, lp(8)); t.addView(timesBox, lp(8))
        islamBox.addView(t, lp(12))

        // স্থান ও গণনা
        val s = card()
        s.addView(tv("📍 স্থান ও গণনা", 16f, bold = true))
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cities.map { it.first })
        sp.setSelection(cities.indexOfFirst { it.first == prefs.place }.coerceAtLeast(0))
        var init = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (init) { init = false; return }
                val c = cities[pos]; prefs.place = c.first; prefs.lat = c.second; prefs.lng = c.third
                locChanged()
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        s.addView(sp, lp(6))
        s.addView(btn("📍 আমার বর্তমান অবস্থান ব্যবহার করুন") {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) useMyLocation()
            else locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }, lp(6))
        s.addView(MaterialSwitch(this).apply {
            text = "আসর: হানাফি পদ্ধতি (বন্ধ = শাফেয়ি)"; setTextColor(Color.WHITE); isChecked = prefs.hanafi
            setOnCheckedChangeListener { _, on -> prefs.hanafi = on; locChanged() }
        }, lp(6))
        s.addView(stepper("সময় সমন্বয় (সব ওয়াক্ত)", "মিনিট", "adj", 0, -15, 15) { locChanged() }, lp(6))
        s.addView(stepper("ওয়াক্তের আগে রিমাইন্ডার", "মিনিট আগে", "pre", 10, 0, 60, "বন্ধ") { locChanged() }, lp(6))
        s.addView(stepper("হিজরি তারিখ সমন্বয়", "দিন", "hadj", 0, -2, 2) { refreshTimes() }, lp(6))
        s.addView(tv("সময় ফোনেই অফলাইনে গণনা হয় (ফজর/এশা ১৮°)। স্থানীয় মসজিদ বা ইসলামিক ফাউন্ডেশনের সময়ের সাথে মিলিয়ে সমন্বয় ঠিক করে নিন।", 11f, MUTED), lp(8))
        islamBox.addView(s, lp(12))

        islamBox.addView(eventCard(Events.adhan, simple = true), lp(12))
        Events.islamic.forEach { islamBox.addView(eventCard(it), lp(12)) }

        // কিবলা
        val q = card()
        q.addView(tv("🧭 কিবলা কম্পাস", 16f, bold = true))
        qv = QiblaView(this)
        q.addView(qv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        qiblaTv = tv("", 12f, MUTED); qiblaTv.gravity = Gravity.CENTER
        q.addView(qiblaTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        q.addView(tv("ফোন সমতলে ধরুন। সবুজ তীর কিবলার দিক নির্দেশ করে; ধাতব বস্তু থেকে দূরে রাখুন।", 11f, MUTED), lp(4))
        islamBox.addView(q, lp(12))

        // তাসবিহ
        val tb = card()
        tb.addView(tv("📿 ডিজিটাল তাসবিহ", 16f, bold = true))
        val count = tv(Bn.d(prefs.int("tasbih", 0)), 44f, bold = true); count.gravity = Gravity.CENTER
        tb.addView(count, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val row = LinearLayout(this)
        row.addView(btn("📿 গণনা করুন (+১)") {
            val n = prefs.int("tasbih", 0) + 1
            prefs.setInt("tasbih", n); count.text = Bn.d(n)
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createOneShot(if (n % 33 == 0) 250L else 25L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }.apply { textSize = 15f }, LinearLayout.LayoutParams(0, dp(64), 2f))
        row.addView(btn("↺ শূন্য") { prefs.setInt("tasbih", 0); count.text = Bn.d(0) }, LinearLayout.LayoutParams(0, dp(64), 1f))
        tb.addView(row, lp(6))
        islamBox.addView(tb, lp(12))
    }

    private fun eventCard(ev: Ev, simple: Boolean = false): LinearLayout {
        val c = card()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv(ev.title, 16f, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (!simple) top.addView(MaterialSwitch(this).apply {
            isChecked = prefs.enabled(ev)
            setOnCheckedChangeListener { _, on -> prefs.setEnabled(ev, on); Scheduler.scheduleNext(this@MainActivity) }
        })
        c.addView(top)

        var edit: EditText? = null
        if (!simple) {
            val e = EditText(this).apply {
                setText(prefs.text(ev)); setTextColor(Color.WHITE); textSize = 14f
                minLines = 2; gravity = Gravity.TOP
                background = bg(BG, 12); setPadding(dp(12), dp(10), dp(12), dp(10))
                doAfterTextChanged { prefs.setText(ev, it.toString()) }
            }
            edit = e
            c.addView(e, lp(8))
        }
        val lab = tv("", 12f, MUTED); labels[ev.id] = lab; refreshLabel(ev)
        c.addView(lab, lp(6))

        val row = LinearLayout(this)
        fun add(b: MaterialButton) = row.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        add(btn("🎵 বাছুন") { pickFor = ev; picker.launch(arrayOf("audio/*")) })
        add(btn("▶ শুনুন") { Voice.play(this, ev, 17, force = true, name = "আসর", min = 10) })
        add(btn("■ থামান") { Voice.stop() })
        add(btn("↺ রিসেট") {
            prefs.setAudio(ev, null)
            if (!simple) { prefs.setText(ev, ev.def); edit?.setText(ev.def) }
            refreshLabel(ev)
        })
        c.addView(row, lp(6))
        return c
    }

    // ---------- আচরণ ----------
    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun batteryOk() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun ensure(force: Boolean) {
        wantBattery = force || prefs.int("askb", 0) == 0
        val need = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val miss = need.filter { !granted(it) }
        if (miss.isEmpty()) afterPerms() else perms.launch(miss.toTypedArray())
    }

    private fun afterPerms() {
        startSvc(); refreshStatus()
        if (wantBattery && !batteryOk()) { prefs.setInt("askb", 1); wantBattery = false; askBattery() }
    }

    private fun startSvc() {
        if (!prefs.service) return
        Scheduler.startService(this)
        Scheduler.scheduleNext(this)
        Scheduler.keepAlive(this)
    }

    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openAutoStart() {
        val c = listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity"
        )
        for ((pk, cl) in c) {
            try { startActivity(Intent().setComponent(ComponentName(pk, cl))); return } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        Toast.makeText(this, "ব্যাটারি > সীমাহীন (Unrestricted) এবং অটো-লঞ্চ/ব্যাকগ্রাউন্ড অ্যাক্টিভিটি চালু করুন", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("MissingPermission")
    private fun useMyLocation() {
        val lm = getSystemService(LocationManager::class.java)
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time }
        if (loc == null) {
            Toast.makeText(this, "অবস্থান পাওয়া যায়নি। লোকেশন চালু করে আবার চেষ্টা করুন", Toast.LENGTH_LONG).show()
            return
        }
        prefs.lat = loc.latitude; prefs.lng = loc.longitude; prefs.place = "আমার অবস্থান"
        locChanged()
    }

    private fun locChanged() { Scheduler.scheduleNext(this); refreshTimes() }

    private fun refreshStatus() {
        fun m(ok: Boolean) = if (ok) "✅" else "❌"
        val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
        statusTv.text = listOf(
            "${m(notif)} নোটিফিকেশন (সেবা চালু রাখতে)",
            "${m(granted(Manifest.permission.READ_PHONE_STATE))} ফোন অনুমতি (কল ও মিসড কল শনাক্ত)",
            "${m(batteryOk())} ব্যাটারি সীমাবদ্ধতা মুক্ত"
        ).joinToString("\n")
    }

    private fun refreshTimes() {
        val t = Scheduler.times(this, 0)
        timesBox.removeAllViews()
        val rows = listOf("ফজর" to "fajr", "সূর্যোদয়" to "sunrise", "যোহর" to "dhuhr", "আসর" to "asr", "মাগরিব" to "maghrib", "এশা" to "isha")
        for ((n, k) in rows) {
            val r = LinearLayout(this).apply { setPadding(0, dp(4), 0, dp(4)) }
            val dim = k == "sunrise"
            r.addView(tv(n, 15f, if (dim) MUTED else Color.WHITE), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            r.addView(tv(Scheduler.fmt(t[k] ?: 0L), 15f, if (dim) MUTED else Color.WHITE, !dim))
            timesBox.addView(r)
        }
        placeTv.text = "📍 ${prefs.place}"
        val hd = HijrahDate.from(LocalDate.now().plusDays(prefs.int("hadj", 0).toLong()))
        val wd = weekdays[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
        hijriTv.text = "🗓 $wd, ${Bn.d(hd.get(ChronoField.DAY_OF_MONTH))} ${hijriMonths[hd.get(ChronoField.MONTH_OF_YEAR) - 1]} ${Bn.d(hd.get(ChronoField.YEAR))} হিজরি"
        updateNext(); updateQibla()
    }

    private fun updateNext() {
        val nx = Scheduler.nextPrayer(this) ?: run { nextTv.text = ""; return }
        val mins = ((nx.second - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(0)
        val h = mins / 60; val m = mins % 60
        nextTv.text = "পরবর্তী: ${nx.first} (${Scheduler.fmt(nx.second)}) — " +
            (if (h > 0) "${Bn.d(h)} ঘণ্টা " else "") + "${Bn.d(m)} মিনিট বাকি"
    }

    private fun updateQibla() {
        val q = PrayerCalc.qibla(prefs.lat, prefs.lng)
        qv.qibla = q.toFloat(); qv.invalidate()
        qiblaTv.text = "কিবলা: উত্তর থেকে ডানদিকে ${Bn.d(q.roundToInt())}°"
    }

    override fun onResume() {
        super.onResume()
        refreshStatus(); refreshTimes()
        handler.post(ticker)
        sm = getSystemService(SensorManager::class.java)
        val s = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s != null) sm?.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_UI)
        else qiblaTv.text = qiblaTv.text.toString() + " (কম্পাস সেন্সর নেই)"
        if (prefs.service) { Scheduler.scheduleNext(this); Scheduler.keepAlive(this) }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        sm?.unregisterListener(sensorListener)
    }
}
