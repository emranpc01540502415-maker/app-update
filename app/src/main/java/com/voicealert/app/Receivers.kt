package com.voicealert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** ফোন চালু, অ্যাপ আপডেট, সময়/টাইমজোন বদল — সব ক্ষেত্রে সেবা ও এলার্ম আবার চালু করে। */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs(c).service) return
        val boot = i.action == Intent.ACTION_BOOT_COMPLETED || i.action == "android.intent.action.QUICKBOOT_POWERON"
        Scheduler.startService(c) { putExtra("boot", boot) }
        Scheduler.scheduleNext(c)
        Scheduler.keepAlive(c)
    }
}

/** নামাজ/রিমাইন্ডারের এলার্ম এবং পাহারাদার এলার্ম। */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs(c).service) return
        when (i.action) {
            "va.FIRE" -> {
                val id = i.getStringExtra("id")
                val name = i.getStringExtra("name") ?: ""
                val min = i.getIntExtra("min", -1)
                Scheduler.startService(c) {
                    if (id != null) { putExtra("fire", id); putExtra("name", name); putExtra("min", min) }
                }
                Scheduler.scheduleNext(c)
            }
            "va.KEEP" -> {
                Scheduler.startService(c)
                Scheduler.keepAlive(c)
                Scheduler.scheduleNext(c)
            }
        }
    }
}
