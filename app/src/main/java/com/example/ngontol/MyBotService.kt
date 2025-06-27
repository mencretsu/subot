package com.example.ngontol
import kotlinx.coroutines.*
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.*
import android.content.*
import android.os.Bundle
import androidx.core.app.NotificationCompat

import java.util.ArrayDeque
import java.util.LinkedHashSet

private const val ID_INPUT = "com.voicemaker.android:id/id_input_edit_text"
private const val ID_SEND  = "com.voicemaker.android:id/id_chat_send_btn"

class MyBotService : AccessibilityService() {

    companion object {
        const val CHANNEL_ID = "BOT_CH"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.example.ngontol.STOP"
        private const val TAG = "BOT"

        /* throttle adaptif */
        private const val MIN_INTERVAL = 1500L   // ≤ 5 unread
        private const val MAX_INTERVAL = 1000L   // > 5 unread

        /* cache read */
        private const val MAX_CACHE = 200
        private const val PREF_KEY  = "processed_keys"

        /* ====== GANTI SESUAI ID DI APLIKASI ====== */
        private const val ID_INPUT = "com.voicemaker.android:id/id_chat_et"
        private const val ID_SEND  = "com.voicemaker.android:id/id_send_btn"
    }

    private val processed = LinkedHashSet<String>()
    private var lastRun   = 0L
    private var interval  = MIN_INTERVAL
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /* -------- lifecycle -------- */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.e("BOT", "✅ SERVICE CONNECTED")

        ensureBotChannel()
        showNotif()
        processed += getSharedPreferences("bot_cache", MODE_PRIVATE)
            .getStringSet(PREF_KEY, emptySet()) ?: emptySet()
        Log.e(TAG, "✅ SERVICE CONNECTED – cache restored (${processed.size})")
    }

    @SuppressLint("ForegroundServiceType")
    private fun showNotif() {
        /* ① intent buka MainActivity ketika notif disentuh */
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /* ② bikin notif TANPA tombol stop */
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bot)
            .setContentTitle("Ngontol")
            .setContentText("Bot berjalan...")
            .setContentIntent(openAppPendingIntent)  // ⬅️ pakai ini
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notif)
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()   // hentikan semua coroutine saat service mati
    }

    /* -------- main loop -------- */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = System.currentTimeMillis()
        if (now - lastRun < interval) return
        lastRun = now

        // Cek status switch skip dari shared prefs
        val skip = getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)
            .getBoolean("skip_cocok_online", false)

        val root = rootInActiveWindow ?: return
        val rows = root.findAccessibilityNodeInfosByViewId(
            "com.voicemaker.android:id/ll_chat_item"
        )
        if (rows.isEmpty()) return

        var unreadOnScreen = 0
        val unreadList = mutableListOf<Triple<AccessibilityNodeInfo, String, String>>()
        for (row in rows) {
            try {
                val name = row.getChild(1)?.text?.toString().orEmpty()
                val msg  = row.getChild(3)?.text?.toString().orEmpty()
                if (name.isBlank() || msg.isBlank()) continue
                val key = "$name|$msg"
                if (key in processed) continue  // sudah dicache (read)

                val unread = findUnreadCount(row)
                if (unread > 0) {
                    // =============== PATCH: SKIP ALL IF SWITCH ON & pattern match ===============
                    val cocokPatterns = listOf("[Cocok]")
                    val partnerPatterns = listOf("[Online]")
                    val msgLow = msg.trim().lowercase()
                    if (skip && (
                                cocokPatterns.any { msgLow.contains(it.lowercase()) }
                                        || partnerPatterns.any { msgLow.contains(it.lowercase()) }
                                )
                    ) {
                        // Anggap pesan ini tidak ada (tidak di-proses sama sekali)
                        Log.e(TAG, "⏩ SKIP ROW $name karena switch ON dan pattern cocok")
                        continue
                    }
                    // ===========================================================================

                    unreadOnScreen++
                    // TAMBAH KE LIST DULU, JANGAN LANGSUNG HANDLE
                    unreadList.add(Triple(row, name, msg))

                } else {
                    processed.add(key)
                    if (processed.size > MAX_CACHE) processed.remove(processed.first())
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Row error: ${e.message}")
            }
        }

        // proses satu-satu SECARA BERURUTAN
        if (unreadList.isNotEmpty()) {
            serviceScope.launch {
                for ((row, name, msg) in unreadList) {
                    try {
                        handleUnread(row, name, msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error reply: ${e.message}")
                    } finally {
                        row.recycle()
                    }

                }
                // setelah semua selesai, simpan cache dan update interval
                getSharedPreferences("bot_cache", MODE_PRIVATE)
                    .edit().putStringSet(PREF_KEY, processed).apply()
                interval = if (unreadOnScreen > 1) MAX_INTERVAL else MIN_INTERVAL
                Log.d(TAG, "🔄 next interval = $interval ms  (unreadOnScreen=$unreadOnScreen)")
            }
        } else {
            // kalau tidak ada unread, tetap update cache & interval
            getSharedPreferences("bot_cache", MODE_PRIVATE)
                .edit().putStringSet(PREF_KEY, processed).apply()
            interval = if (unreadOnScreen > 1) MAX_INTERVAL else MIN_INTERVAL
            Log.d(TAG, "🔄 next interval = $interval ms  (unreadOnScreen=$unreadOnScreen)")
        }
    }
    /* -------- auto-reply -------- */
    private fun clickFirstClickable(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null && !cur.isClickable) cur = cur.parent
        return cur?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }
    private fun waitInputBox(timeoutMs: Long = 3000): AccessibilityNodeInfo? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            rootInActiveWindow
                ?.findAccessibilityNodeInfosByViewId("com.voicemaker.android:id/id_input_edit_text")
                ?.firstOrNull()?.let { return it }
            Thread.sleep(80)
        }
        Log.e(TAG, "Input box not found")
        return null
    }
    private suspend fun sendReply(input: AccessibilityNodeInfo, text: String) {
        // set text
        val delayTime = if (text.length <= 30) {
            (370..590).random().toLong()
        } else {
            (670..2490).random().toLong()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            val clip = ClipData.newPlainText("reply", text)
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        delay(delayTime)  // delay biar natural

        // klik tombol kirim
        rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId("com.voicemaker.android:id/id_chat_send_btn")
            ?.firstOrNull()
            ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ?: input.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
    }
    private fun handleUnread(row: AccessibilityNodeInfo, name: String, rawMsg: String) {
        Log.e(TAG, "🟡 Try click row for $name")

        // ① Klik item (cari parent yang clickable)
        if (!clickFirstClickable(row)) {
            Log.e(TAG, "❌ Tidak bisa klik row $name")
            return
        }

        Thread.sleep(500) // beri jeda transisi animasi

        // ② Tunggu input box muncul
        val input = waitInputBox() ?: run {
            Log.e(TAG, "❌ Input tidak ditemukan setelah klik $name")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // ③ Ambil data persona
        val persona = PersonaManager.getPersona(this) ?: run {
            Log.e(TAG, "❌ Persona belum diset")
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        Log.e(TAG, "📨 Latest message from $name: $rawMsg")
        // ⑤ Pola pesan khusus
        val hiPatterns = listOf(
            "Aku sudah mengikutimu. Kita bisa berteman jika sudah saling mengikuti."
        )
        val partnerPatterns = listOf(
            "[Online]"
        )
        val cocokPatterns = listOf("[Cocok]")
        // ⑥ Balasan AI\
        val genZOpeners = listOf(
            "random tapi vibes km lucu",
            "niat swipe ga sih wkwk",
            "km suka roti sobek ga?",
            "lagi di ksur juga ga?",
            "km kayaknya doyan nyusu~",
            "btw, suara kamu cemana?",
            "km vibesnya anak mager ya?",
            "random, tapi mau kenal?",
            "lagi nunggu chat seru nih..",
            "km cocok diajak debat micin",
            "abis cuci muka apa hati?",
            "km tim es teh apa kopi?",
            "km anak senja atau hujan?",
            "km jago masak mie ga?",
            "eh km mirip mantan aku",
            "pake parfum apa tuh? wangi",
            "cuma kamu yg aku swipe",
            "lagi mikirin kamu gajelas",
            "kalo bales, jodoh ya?",
            "km suka dengerin lagu apa?"
        )
        val opener = genZOpeners.random()

        serviceScope.launch {
            val replyText = when {
                hiPatterns.any { rawMsg.contains(it) } -> "masa sih hehee.."
                cocokPatterns.any { rawMsg.contains(it) } -> opener
                partnerPatterns.any {rawMsg.contains(it) } -> "baru online aja niihh"
                else -> {
                    delay(230)
                    val prompt = "$name: ${rawMsg.trim()}"
                    val ai = withContext(Dispatchers.IO) {
                        GeminiApi.generateReply(prompt, persona)
                    } ?: "Hmm..."
                    // ==== FILTERS ====
                    var clean = ai
                    // 1. Replace nama pengirim dengan "ganteng"
                    clean = clean.replace(Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE), "bebb")
                    // 2. Replace ! dengan ...
                    clean = clean.replace("!", "...")
                    clean = clean.replace("~", "...")
                    clean = clean.replace("*", " ")
                    clean = clean.replace(persona.botName, "");
                    clean = clean.replace(":", "")
                    clean = clean.replace("Hai", "iyh")
                    clean = clean.replace("\uD83D\uDE1C", " ")
                    clean = clean.replace("\uD83E\uDD2A", " ")
                    // 3. Filter forbidden words 😜
                    val forbidden = listOf(
                        "whatsapp","video","tiktok","instagram","ig","fb","facebook",
                        "telegram","tele","snapchat","michat","messenger","nomor","spesial"
                    )
                    forbidden.forEach {
                        clean = clean.replace(Regex(it, RegexOption.IGNORE_CASE), " ")
                    }
                    clean = clean.replace(Regex("\\s{2,}"), "").trim()
                    // 6. Deteksi gambar/link/teks Inggris ngaco
                    val urlRegex = Regex("(https?://\\S+|www\\.\\S+)")

                    val imgRegex = Regex("(!\\[.*?\\]\\(.*?\\)|\\[image.*?\\]|\\.jpg|\\.jpeg|\\.png|\\.gif|\\.webp)", RegexOption.IGNORE_CASE)
                    val englishWeirdRegex = Regex("\\b(the|you|your|are|is|am|my|me|a|an|for|with|can|will|shall|of|and|to|in|on|from|by|it|at|as)\\b", RegexOption.IGNORE_CASE)

                    // Cek jika ada gambar atau link atau text Inggris ngaco
                    if (urlRegex.containsMatchIn(clean) ||
                        imgRegex.containsMatchIn(clean) ||
                        englishWeirdRegex.containsMatchIn(clean)
                    ) {
                        clean = "ehhh..."
                    }
                    // 5. Lowercase semua
                    clean = clean.lowercase()
                    clean
                }
            }
            sendReply(input, replyText)

            delay(100)
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.e(TAG, "✉️ Replied to $name with: $replyText")
        }
    }
    /* -------- unread badge helper -------- */

    private fun findUnreadCount(row: AccessibilityNodeInfo): Int {
        row.findAccessibilityNodeInfosByViewId(
            "com.voicemaker.android:id/id_unread_tcv"
        ).firstOrNull()?.text?.toString()?.toIntOrNull()?.let { return it }

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until row.childCount) row.getChild(i)?.let { stack.add(it) }

        while (stack.isNotEmpty()) {
            val cur = stack.removeFirst()
            val txt = cur.text?.toString() ?: ""
            if (cur.className == "android.widget.TextView" && txt.matches(Regex("\\d+"))) {
                val count = txt.toInt()
                cur.recycle()
                return count
            }
            for (i in 0 until cur.childCount) cur.getChild(i)?.let { stack.add(it) }
            cur.recycle()
        }
        return 0
    }

    private fun ensureBotChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, "Bot Channel", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }
}