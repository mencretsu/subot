package com.example.ngontol

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.ngontol.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private var botRunning = false         // flag Start/Stop

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // === Tambahkan ini untuk skip switch ===
        // In onCreate, after skipSwitch is initialized
        val skipSwitch = b.switch1

// set custom color for ON/OFF state
        skipSwitch.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences("bot_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("skip_cocok_online", isChecked).apply()
            if (isChecked) {
                skipSwitch.thumbTintList = getColorStateList(R.color.green_500) // or Color.GREEN
                skipSwitch.trackTintList = getColorStateList(R.color.green_200)
            } else {
                skipSwitch.thumbTintList = getColorStateList(android.R.color.darker_gray)
                skipSwitch.trackTintList = getColorStateList(android.R.color.darker_gray)
            }
        }

// set initial color state
        if (skipSwitch.isChecked) {
            skipSwitch.thumbTintList = getColorStateList(R.color.green_500)
            skipSwitch.trackTintList = getColorStateList(R.color.green_200)
        } else {
            skipSwitch.thumbTintList = getColorStateList(android.R.color.darker_gray)
            skipSwitch.trackTintList = getColorStateList(android.R.color.darker_gray)
        }
        refreshUI()

        /* --- tombol Add / Edit Persona --- */
//        b.btnAddPersona.setOnClickListener {
//            val hasPersona = PersonaManager.getPersona(this) != null
//            if (hasPersona) {
//                // sudah ada persona, tampil info tidak editable
//                b.panelInfo.visibility = View.VISIBLE
//            } else {
//                // belum ada persona, tampilkan form input
//                b.panelForm.visibility = View.VISIBLE
//            }
//        }
        /* --- Save Persona --- */

        b.btnSavePersona.setOnClickListener {
            val persona = Persona(
                botName = b.etBotName.text.toString(),
                gender  = b.etGender.text.toString(),
                address = b.etAddress.text.toString(),
                hobby   = b.etHobby.text.toString(),
                apiKey  = b.etApiKey.text.toString()
            )
            if (persona.botName.isBlank() || persona.apiKey.isBlank()) {
                toast("Isi nama & API key!")
                return@setOnClickListener
            }
            PersonaManager.savePersona(this, persona)
            toast("PROFIL TERSIMPAN ✅")
//            b.panelForm.visibility = View.GONE
            refreshUI()
        }

//        /* --- Delete Persona --- */
//        b.btnDeletePersona.setOnClickListener {
//            PersonaManager.clearPersona(this)
//            toast("PROFIL deleted")
//            b.panelInfo.visibility = View.GONE
//            b.btnAddPersona.visibility = View.VISIBLE
//
//            refreshUI()
//        }

        /* --- Start / Stop bot --- */
        b.btnStart.setOnClickListener {
            if (!botRunning) {
                // ingin START
                val ready = checkPrerequisites()
                if (ready) {
                    botRunning = true
                    b.btnStart.text = "Stop Bot"
                    b.btnStart.setBackgroundColor("#FF1744".toColorInt())

                    b.tvStatus.append("\nBot AKTIF... 🚀")
                    b.tvStatus.setTextColor("#00E676".toColorInt())

                }
            } else {
                // ingin STOP
                botRunning = false
                b.btnStart.text = "Start Bot"
                val berhentiText = "\nBot BERHENTI.. ⏹️"
                val spannable = SpannableString(berhentiText)
                spannable.setSpan(
                    ForegroundColorSpan("#FF1744".toColorInt()),
                    0,
                    berhentiText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                b.tvStatus.append(spannable)
                b.btnStart.setBackgroundColor("#00E676".toColorInt())
            }
        }
    }

    /* -------- helper fungsi -------- */

    @SuppressLint("SetTextI18n")
    private fun refreshUI() {
        val persona = PersonaManager.getPersona(this)
        val skipOn = getSharedPreferences("bot_prefs", MODE_PRIVATE)
            .getBoolean("skip_cocok_online", false)

        val switchStatus = if (skipOn) "Skip: ON" else "Skip: OFF"
        if (persona == null) {
            // belum ada profile
//            b.panelInfo.visibility = View.GONE
//            b.btnAddPersona.visibility = View.VISIBLE
//
//            b.btnAddPersona.text = "TAMBAH PROFIL"
            b.tvPersonaInfo.text = "== Profil KOSONG =="
        }
//        }
//        else {
//            // sudah ada

////            b.btnAddPersona.visibility = View.VISIBLE
//            b.tvPersonaInfo.text = "profil tersimpan"
//            b.panelInfo.visibility = View.VISIBLE
////            b.btnAddPersona.visibility = View.INVISIBLE
//        }
        else {
            val info = """
            Profil tersimpan
            ${persona.botName} | API Key: ${persona.apiKey}
            $switchStatus
        """.trimIndent()
            // Versi fade in animasi
            b.tvPersonaInfo.text = "Menyimpan profil..."
            b.tvPersonaInfo.alpha = 0f
            b.tvPersonaInfo.animate()
                .alpha(1f)
                .setDuration(350)
                .withEndAction {
                    b.tvPersonaInfo.postDelayed({
                        b.tvPersonaInfo.text = info
                        b.tvPersonaInfo.alpha = 0f
                        b.tvPersonaInfo.animate().alpha(1f).setDuration(250).start()
                    }, 700)
                }
                .start()
        }
    }

    /** cek persona tersimpan & accessibility aktif */
    private fun checkPrerequisites(): Boolean {
        val personaOK = PersonaManager.getPersona(this) != null
        val accOK = isServiceEnabled()


        val statusBuilder = SpannableStringBuilder()

// Profile status
        if (personaOK) {
            statusBuilder.append("Checking profile status: [200 ok]\n")
        } else {
            val start = statusBuilder.length
            statusBuilder.append("Checking profile status: [null]\n")
            statusBuilder.setSpan(
                ForegroundColorSpan("#FF1744".toColorInt()),
                start,
                statusBuilder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

// Accessibility status
        if (accOK) {
            statusBuilder.append("Checking accessibility status:[200 ok]")
        } else {
            val start = statusBuilder.length
            statusBuilder.append("Checking accessibility status:[off]")
            statusBuilder.setSpan(
                ForegroundColorSpan("#FF1744".toColorInt()),
                start,
                statusBuilder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        b.tvStatus.text = statusBuilder

        if (!personaOK) toast("Isi & save profil dulu!")
        if (!accOK) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return personaOK && accOK
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return list.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
