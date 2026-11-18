package jp.jamsesion.hitoiki

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var modeLabel: TextView
    private lateinit var timeText: TextView
    private lateinit var roundText: TextView
    private lateinit var startBtn: Button
    private lateinit var settingsCard: LinearLayout
    private lateinit var workVal: TextView
    private lateinit var restVal: TextView
    private lateinit var permCard: LinearLayout
    private lateinit var permText: TextView
    private lateinit var permBtn: Button

    private val ticker = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        Timer.ensureChannels(this)
        setContentView(build())
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }

    private fun build(): View {
        val c = this
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 18); setPadding(p, p, p, Ui.dp(c, 32))
        }
        col.addView(Ui.text(c, "ひと息タイマー", 22f, bold = true, center = true))

        modeLabel = Ui.text(c, "", 17f, bold = true, center = true).apply {
            val h = Ui.dp(c, 18); val v = Ui.dp(c, 6); setPadding(h, v, h, v)
        }
        col.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER; addView(modeLabel)
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 12)))

        timeText = Ui.text(c, "25:00", 76f, bold = true, center = true)
        col.addView(timeText, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        roundText = Ui.text(c, "", 16f, Ui.SUB, center = true)
        col.addView(roundText)

        val controls = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = Ui.button(c, "スタート", Ui.INK, 0xFFFFFFFF.toInt()) { onStart() }
        controls.addView(startBtn, Ui.lp(0, WRAP_CONTENT, 2f))
        val reset = Ui.button(c, "リセット", 0xFFE9E4DC.toInt(), Ui.INK) { Timer.reset(c); render() }
        controls.addView(reset, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 12) })
        col.addView(controls, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 20)))

        // 許可が足りないときの案内
        permCard = Ui.card(c, Ui.WARN)
        permText = Ui.text(c, "", 15f)
        permCard.addView(permText)
        permBtn = Ui.button(c, "設定を開く", Ui.INK, 0xFFFFFFFF.toInt(), 16f, 50) { openPermSettings() }
        permCard.addView(permBtn, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 10)))
        col.addView(permCard, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))

        // 時間の設定
        settingsCard = Ui.card(c)
        settingsCard.addView(Ui.text(c, "時間の設定", 16f, bold = true))
        val wRow = stepperRow("使う時間", "スマホを使う時間", true)
        workVal = wRow.second
        settingsCard.addView(wRow.first, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        val rRow = stepperRow("休憩時間", "画面から離れる時間", false)
        restVal = rRow.second
        settingsCard.addView(rRow.first, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        val presets = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(25 to 5, 45 to 10, 15 to 5).forEachIndexed { i, (w, r) ->
            val b = Ui.button(c, "${w}分＋${r}分", 0xFFF3EFE9.toInt(), Ui.INK, 14f, 44) {
                Timer.setMinutes(c, w, r); render()
            }
            presets.addView(b, Ui.lp(0, WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 8) })
        }
        settingsCard.addView(presets, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 12)))
        col.addView(settingsCard, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))

        // お知らせ
        val note = Ui.card(c)
        note.addView(Ui.text(c, "お知らせの方法", 16f, bold = true))
        note.addView(Ui.text(c,
            "・時間になると、YouTubeなどを見ていても画面の上に割り込んで、音と振動で知らせます。\n" +
            "・画面が消えているときは、全画面で知らせます。\n" +
            "・音は「OK」を押すまで鳴り、押さなくても1分で止まります。\n" +
            "・音の大きさは、Galaxyの「アラーム」の音量で決まります（普段の通知音とは別）。\n" +
            "・このアプリを閉じても、タイマーは動き続けます。",
            14f, Ui.SUB), Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        note.addView(Ui.button(c, "音を試す", 0xFFF3EFE9.toInt(), Ui.INK, 16f, 50) {
            Timer.showAlert(c, true, null, test = true)
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 12)))
        note.addView(Ui.button(c, "音・振動の細かい設定", 0xFFF3EFE9.toInt(), Ui.INK, 16f, 50) {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Timer.CH_ALERT))
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        col.addView(note, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))

        return ScrollView(c).apply { setBackgroundColor(Ui.BG); addView(col) }
    }

    private fun stepperRow(label: String, sub: String, isWork: Boolean): Pair<LinearLayout, TextView> {
        val c = this
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        left.addView(Ui.text(c, label, 17f, bold = true))
        left.addView(Ui.text(c, sub, 13f, Ui.SUB))
        row.addView(left, Ui.lp(0, WRAP_CONTENT, 1f))
        val v = Ui.text(c, "", 22f, bold = true, center = true)
        fun change(d: Int) {
            val s = Timer.load(c)
            val w = if (isWork) (s.workMin + d).coerceIn(1, 120) else s.workMin
            val r = if (!isWork) (s.restMin + d).coerceIn(1, 60) else s.restMin
            Timer.setMinutes(c, w, r); render()
        }
        row.addView(Ui.button(c, "−", Ui.CHIP, Ui.INK, 24f, 52) { change(-1) }, Ui.lp(Ui.dp(c, 56), WRAP_CONTENT))
        row.addView(v, Ui.lp(Ui.dp(c, 80), WRAP_CONTENT))
        row.addView(Ui.button(c, "＋", Ui.CHIP, Ui.INK, 24f, 52) { change(1) }, Ui.lp(Ui.dp(c, 56), WRAP_CONTENT))
        return row to v
    }

    private fun onStart() {
        val s = Timer.load(this)
        when {
            s.running -> Timer.pause(this)
            s.phase == Timer.IDLE -> Timer.start(this)
            else -> Timer.resume(this)
        }
        render()
    }

    private fun render() {
        val s = Timer.load(this)
        timeText.text = Timer.fmt(s.left())
        val (label, fg, bg) = when (s.phase) {
            Timer.WORK -> Triple(if (s.running) "使う時間" else "使う時間（一時停止中）", Ui.WORK, Ui.WORK_SOFT)
            Timer.REST -> Triple(if (s.running) "休憩中" else "休憩中（一時停止中）", Ui.REST, Ui.REST_SOFT)
            else -> Triple("待機中", Ui.SUB, 0xFFECE8E1.toInt())
        }
        modeLabel.text = label
        modeLabel.setTextColor(fg)
        modeLabel.background = Ui.round(bg, 999, this)
        timeText.setTextColor(if (s.phase == Timer.REST) Ui.REST else Ui.INK)
        roundText.text = when (s.phase) {
            Timer.WORK -> "${s.round}回目"
            Timer.REST -> "画面から離れましょう"
            else -> "「スタート」で始まります"
        }
        startBtn.text = when {
            s.running -> "一時停止"
            s.phase == Timer.IDLE -> "スタート"
            else -> "再開"
        }
        workVal.text = "${s.workMin}分"
        restVal.text = "${s.restMin}分"
        settingsCard.alpha = if (s.phase == Timer.IDLE) 1f else 0.45f
        setEnabledDeep(settingsCard, s.phase == Timer.IDLE)

        val msg = permissionProblem()
        permCard.visibility = if (msg == null) View.GONE else View.VISIBLE
        if (msg != null) permText.text = msg
    }

    private fun setEnabledDeep(v: View, on: Boolean) {
        v.isEnabled = on
        if (v is LinearLayout) for (i in 0 until v.childCount) setEnabledDeep(v.getChildAt(i), on)
    }

    private fun notificationsAllowed(): Boolean = Timer.nm(this).areNotificationsEnabled()

    private fun permissionProblem(): String? = when {
        !notificationsAllowed() ->
            "⚠ お知らせ（通知）が許可されていません。\n下のボタンから「通知を許可」をオンにしてください。"
        !Timer.canFullScreen(this) ->
            "⚠ 画面が消えているときの全画面表示が許可されていません。\n下のボタンから「全画面通知」を許可してください。"
        else -> null
    }

    private fun openPermSettings() {
        val i = if (!notificationsAllowed()) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try { startActivity(i) } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
