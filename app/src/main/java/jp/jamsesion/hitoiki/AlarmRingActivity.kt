package jp.jamsesion.hitoiki

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast

/**
 * アラームが鳴ったときに出る、画面いっぱいの画面。
 * 大きな「止める」ボタンと「5分後にもう一度」ボタン、メモを表示します。
 */
class AlarmRingActivity : Activity() {
    companion object { var showing: AlarmRingActivity? = null }

    private val handler = Handler(Looper.getMainLooper())
    private var alarmId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showing = this
        show()
        handler.postDelayed({ finish() }, Alarms.RING_LIMIT_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show()
    }

    private fun show() {
        alarmId = intent.getIntExtra("id", 0)
        val a = Alarms.get(this, alarmId)
        val color = Ui.WORK
        val white = 0xFFFFFFFF.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(color)
            val p = Ui.dp(this@AlarmRingActivity, 24)
            setPadding(p, Ui.dp(this@AlarmRingActivity, 40), p, p)
        }
        root.addView(Ui.text(this, "⏰ アラーム", 20f, white, true, true), Ui.lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(Ui.text(this, a?.let { Alarms.timeText(it) } ?: "", 64f, white, true, true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 4)))
        val memo = a?.memo?.ifBlank { null } ?: "時間です"
        root.addView(Ui.text(this, memo, 26f, white, true, true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 8)))

        val stopBtn = Ui.button(this, "止める", white, color, 60f, 180) { stop() }
        stopBtn.background = Ui.round(white, 40, this)
        root.addView(stopBtn, Ui.lp(MATCH_PARENT, 0, 1f, top = Ui.dp(this, 24)))

        val snz = Ui.button(this, "${Alarms.SNOOZE_MIN}分後にもう一度", 0x40FFFFFF, white, 22f, 72) { snooze() }
        root.addView(snz, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 14)))

        root.addView(Ui.text(this, "押さなくても3分で音は止まります", 15f, white, center = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 12)))
        setContentView(root)
    }

    private fun stop() {
        Alarms.stopRing(this, alarmId)
        finish()
    }

    private fun snooze() {
        Alarms.snooze(this, alarmId)
        Toast.makeText(this, "${Alarms.SNOOZE_MIN}分後にもう一度鳴ります", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (showing === this) showing = null
        super.onDestroy()
    }
}
