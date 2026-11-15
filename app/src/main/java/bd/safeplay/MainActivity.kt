package bd.safeplay

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 28)
            setBackgroundColor(0xFFF4F7F5.toInt())
        }
        root.addView(TextView(this).apply {
            text = "SafePlay BD"; textSize = 30f
            setTextColor(0xFF123B2A.toInt()); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "জুয়া ও ক্যাসিনো সাইট ব্লক করুন"; textSize = 16f
            setTextColor(0xFF52635A.toInt()); gravity = Gravity.CENTER
            setPadding(0, 12, 0, 32)
        })
        status = TextView(this).apply {
            textSize = 19f; setTextColor(0xFF123B2A.toInt())
            gravity = Gravity.CENTER; setPadding(0, 24, 0, 24)
        }
        toggle = Button(this).apply { textSize = 17f; setOnClickListener { toggleProtection() } }
        root.addView(status)
        root.addView(toggle, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "পরিচিত ঝুঁকিপূর্ণ ডোমেইন DNS পর্যায়ে ব্লক হয়। সব নতুন সাইট বা বিজ্ঞাপন শনাক্তের নিশ্চয়তা নেই।"
            textSize = 14f; setTextColor(0xFF52635A.toInt())
            gravity = Gravity.CENTER; setPadding(0, 28, 0, 0)
        })
        setContentView(root)
        refreshStatus()
    }

    override fun onResume() { super.onResume(); if (::status.isInitialized) refreshStatus() }

    private fun toggleProtection() {
        if (SafePlayVpnService.isRunning) {
            stopService(Intent(this, SafePlayVpnService::class.java))
            refreshStatus()
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(prepare, VPN_REQUEST)
        } else startProtection()
    }

    @Deprecated("Legacy callback retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST && resultCode == RESULT_OK) startProtection()
    }

    private fun startProtection() {
        val intent = Intent(this, SafePlayVpnService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        status.postDelayed({ refreshStatus() }, 500)
    }

    private fun refreshStatus() {
        val running = SafePlayVpnService.isRunning
        status.text = if (running) "● সুরক্ষা চালু আছে" else "○ সুরক্ষা বন্ধ আছে"
        toggle.text = if (running) "সুরক্ষা বন্ধ করুন" else "সুরক্ষা চালু করুন"
    }

    companion object { private const val VPN_REQUEST = 4102 }
}
