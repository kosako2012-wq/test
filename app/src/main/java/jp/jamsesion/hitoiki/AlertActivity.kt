package jp.jamsesion.hitoiki

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout

/**
 * 時間になったときに出る、画面いっぱいの「止める」画面。
 * 大きなボタン（または画面のどこか）を押すと音が止まり、元の画面（YouTubeなど）に戻ります。
 */
class AlertActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

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
        show()
        // 押さなくても1分で閉じる
        handler.postDelayed({ finish() }, Timer.ALERT_LIMIT_MS)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show()
    }

    private fun show() {
        val toRest = intent.getBooleanExtra("toRest", true)
        val color = if (toRest) Ui.REST else Ui.WORK
        val white = 0xFFFFFFFF.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(color)
            val p = Ui.dp(this@AlertActivity, 24); setPadding(p, Ui.dp(this@AlertActivity, 48), p, p)
            setOnClickListener { stop() }   // 画面のどこを押しても止まる
        }
        root.addView(Ui.text(this, intent.getStringExtra("title") ?: "", 34f, white, true, true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(Ui.text(this, intent.getStringExtra("text") ?: "", 18f, white, center = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 12)))

        // 画面の大部分をしめる、大きな「止める」ボタン
        val stopBtn = Ui.button(this, "止める", white, color, 64f, 200) { stop() }
        stopBtn.background = Ui.round(white, 40, this)
        root.addView(stopBtn, Ui.lp(MATCH_PARENT, 0, 1f, top = Ui.dp(this, 28)))

        root.addView(Ui.text(this, "押すと音が止まり、元の画面に戻ります", 15f, white, center = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(this, 14)))
        setContentView(root)
    }

    private fun stop() {
        Timer.nm(this).cancel(Timer.ID_ALERT)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
