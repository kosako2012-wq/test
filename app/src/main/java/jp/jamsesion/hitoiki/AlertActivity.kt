package jp.jamsesion.hitoiki

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout

/** 画面が消えているとき・ロック中に出る、全画面のお知らせ */
class AlertActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val toRest = intent.getBooleanExtra("toRest", true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(if (toRest) Ui.REST else Ui.WORK)
            val p = Ui.dp(this@AlertActivity, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.text(this, intent.getStringExtra("title") ?: "", 36f, 0xFFFFFFFF.toInt(), true, true))
        root.addView(Ui.text(this, intent.getStringExtra("text") ?: "", 19f, 0xFFFFFFFF.toInt(), center = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 16)))
        root.addView(Ui.button(this, "OK", 0xFFFFFFFF.toInt(), Ui.INK, 22f, 68) { close() },
            Ui.lp(Ui.dp(this, 220), WRAP_CONTENT, top = Ui.dp(this, 36)))
        setContentView(root)

        // 押さなくても1分で閉じる
        handler.postDelayed({ finish() }, Timer.ALERT_LIMIT_MS)
    }

    private fun close() {
        Timer.nm(this).cancel(Timer.ID_ALERT)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
