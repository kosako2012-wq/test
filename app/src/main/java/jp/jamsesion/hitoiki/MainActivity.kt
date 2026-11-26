package jp.jamsesion.hitoiki

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

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

    // 切り替え（タイマー／アラーム）
    private lateinit var tabTimer: Button
    private lateinit var tabAlarm: Button
    private lateinit var timerBox: LinearLayout
    private lateinit var alarmBox: LinearLayout
    private lateinit var alarmList: LinearLayout
    private var tab = "timer"

    // アラームを直している途中の音
    private var editSound = ""
    private var editSoundLabel: TextView? = null
    private var preview: Ringtone? = null
    private val PICK_SOUND = 77

    private val ticker = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        Timer.ensureChannels(this)
        tab = getSharedPreferences("ui", MODE_PRIVATE).getString("tab", "timer") ?: "timer"
        intent?.getStringExtra("tab")?.let { tab = it }
        setContentView(build())
        showTab(tab)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("tab")?.let { showTab(it) }
    }

    override fun onResume() { super.onResume(); handler.post(ticker); renderAlarms() }
    override fun onPause() { handler.removeCallbacks(ticker); stopPreview(); super.onPause() }

    private fun build(): View {
        val c = this
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 18); setPadding(p, p, p, Ui.dp(c, 32))
        }
        col.addView(Ui.text(c, "ひと息タイマー", 22f, bold = true, center = true))

        // タイマー／アラームの切り替え
        val tabs = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        tabTimer = Ui.button(c, "⏳ タイマー", Ui.INK, 0xFFFFFFFF.toInt(), 17f, 50) { showTab("timer") }
        tabAlarm = Ui.button(c, "⏰ アラーム", Ui.CHIP, Ui.INK, 17f, 50) { showTab("alarm") }
        tabs.addView(tabTimer, Ui.lp(0, WRAP_CONTENT, 1f))
        tabs.addView(tabAlarm, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 10) })
        col.addView(tabs, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 12)))

        // 許可が足りないときの案内（どちらの画面でも出す）
        permCard = Ui.card(c, Ui.WARN)
        permText = Ui.text(c, "", 15f)
        permCard.addView(permText)
        permBtn = Ui.button(c, "設定を開く", Ui.INK, 0xFFFFFFFF.toInt(), 16f, 50) { openPermSettings() }
        permCard.addView(permBtn, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 10)))
        col.addView(permCard, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 14)))

        timerBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        buildTimer(timerBox)
        col.addView(timerBox, Ui.lp(MATCH_PARENT, WRAP_CONTENT))

        alarmBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        buildAlarm(alarmBox)
        col.addView(alarmBox, Ui.lp(MATCH_PARENT, WRAP_CONTENT))

        return ScrollView(c).apply { setBackgroundColor(Ui.BG); addView(col) }
    }

    private fun showTab(t: String) {
        tab = if (t == "alarm") "alarm" else "timer"
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("tab", tab).apply()
        val isAlarm = tab == "alarm"
        timerBox.visibility = if (isAlarm) View.GONE else View.VISIBLE
        alarmBox.visibility = if (isAlarm) View.VISIBLE else View.GONE
        tabTimer.background = Ui.round(if (isAlarm) Ui.CHIP else Ui.INK, 16, this)
        tabTimer.setTextColor(if (isAlarm) Ui.INK else 0xFFFFFFFF.toInt())
        tabAlarm.background = Ui.round(if (isAlarm) Ui.INK else Ui.CHIP, 16, this)
        tabAlarm.setTextColor(if (isAlarm) 0xFFFFFFFF.toInt() else Ui.INK)
        if (isAlarm) renderAlarms()
    }

    // ================= タイマー =================
    private fun buildTimer(col: LinearLayout) {
        val c = this
        modeLabel = Ui.text(c, "", 17f, bold = true, center = true).apply {
            val h = Ui.dp(c, 18); val v = Ui.dp(c, 6); setPadding(h, v, h, v)
        }
        col.addView(LinearLayout(c).apply {
            gravity = Gravity.CENTER; addView(modeLabel)
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))

        timeText = Ui.text(c, "25:00", 76f, bold = true, center = true)
        col.addView(timeText, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        roundText = Ui.text(c, "", 16f, Ui.SUB, center = true)
        col.addView(roundText)

        val controls = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = Ui.button(c, "スタート", Ui.INK, 0xFFFFFFFF.toInt()) { onStartPressed() }
        controls.addView(startBtn, Ui.lp(0, WRAP_CONTENT, 2f))
        val reset = Ui.button(c, "リセット", 0xFFE9E4DC.toInt(), Ui.INK) { Timer.reset(c); render() }
        controls.addView(reset, Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 12) })
        col.addView(controls, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 20)))

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
            "・時間になると、YouTubeなどを見ていても画面いっぱいに「止める」ボタンが出て、音と振動で知らせます。\n" +
            "・画面が消えているときは、全画面で知らせます。\n" +
            "・大きな「止める」ボタンを押すと音が止まります。押さなくても1分で止まります。\n" +
            "・休憩が終わると音だけで知らせて、タイマーは自動で終わります（くり返しません）。\n" +
            "・途中でやめるときは、このアプリで「リセット」を押します。\n" +
            "・音の大きさは、Galaxyの「アラーム」の音量で決まります（普段の通知音とは別）。\n" +
            "・このアプリを閉じても、タイマーは動き続けます。",
            14f, Ui.SUB), Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        note.addView(Ui.button(c, "音を試す", 0xFFF3EFE9.toInt(), Ui.INK, 16f, 50) {
            Timer.showAlert(c, true, null, test = true)
            startActivity(Intent(c, AlertActivity::class.java)
                .putExtra("title", "お知らせのテスト").putExtra("text", "時間になると、この画面が出ます").putExtra("toRest", true))
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 12)))
        note.addView(Ui.button(c, "音・振動の細かい設定", 0xFFF3EFE9.toInt(), Ui.INK, 16f, 50) {
            startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Timer.CH_ALERT))
        }, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        col.addView(note, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))
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

    private fun onStartPressed() {
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
            Timer.WORK -> "終わったら休憩です"
            Timer.REST -> "休憩が終わると自動で終了します"
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

    // ================= アラーム =================
    private fun buildAlarm(col: LinearLayout) {
        val c = this
        col.addView(Ui.button(c, "＋ アラームを追加", Ui.WORK, 0xFFFFFFFF.toInt(), 19f, 60) { openEditor(null) },
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))
        alarmList = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(alarmList, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 6)))

        val note = Ui.card(c)
        note.addView(Ui.text(c, "アラームの使い方", 16f, bold = true))
        note.addView(Ui.text(c,
            "・カードを押すと、時刻・曜日・メモ・音を直せます。右のスイッチでオン／オフ。\n" +
            "・曜日を選ばないと「1回だけ」。鳴ったあと自動でオフになります。\n" +
            "・鳴ると、画面が消えていても、YouTubeなどを見ていても、画面いっぱいに「止める」「○分後にもう一度」（スヌーズ）が出ます。スヌーズの時間はアラームごとに選べます。\n" +
            "・押さなくても3分で音は止まります。\n" +
            "・音の大きさは、Galaxyの「アラーム」の音量で決まります。",
            14f, Ui.SUB), Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 8)))
        col.addView(note, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 16)))
    }

    private fun renderAlarms() {
        if (!::alarmList.isInitialized) return
        val c = this
        alarmList.removeAllViews()
        val items = Alarms.list(c)
        if (items.isEmpty()) {
            alarmList.addView(Ui.text(c, "まだアラームはありません。\n上の「＋ アラームを追加」から登録できます。", 15f, Ui.SUB, center = true),
                Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 18)))
            return
        }
        items.forEach { a ->
            val card = Ui.card(c)
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            val on = a.enabled
            left.addView(Ui.text(c, Alarms.timeText(a), 40f, if (on) Ui.INK else Ui.SUB, bold = true))
            left.addView(Ui.text(c, Alarms.daysText(a.days) + "　スヌーズ${a.snooze}分", 15f, if (on) Ui.WORK else Ui.SUB, bold = true))
            if (a.memo.isNotBlank()) left.addView(Ui.text(c, a.memo, 16f, if (on) Ui.INK else Ui.SUB),
                Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 2)))
            val snz = Alarms.snoozeAt(c, a.id)
            val status = when {
                snz > System.currentTimeMillis() -> "スヌーズ中（もう一度鳴ります）"
                on -> "次は ${Alarms.nextLabel(a)}"
                else -> "オフ"
            }
            left.addView(Ui.text(c, status, 13f, Ui.SUB), Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 2)))
            row.addView(left, Ui.lp(0, WRAP_CONTENT, 1f))
            val sw = Switch(c).apply {
                isChecked = on
                scaleX = 1.3f; scaleY = 1.3f
                setOnCheckedChangeListener { _, checked ->
                    Alarms.setEnabled(c, a.id, checked)
                    if (checked) Alarms.get(c, a.id)?.let { toast(Alarms.untilText(it)) }
                    handler.post { renderAlarms() }
                }
            }
            row.addView(sw, Ui.lp(WRAP_CONTENT, WRAP_CONTENT))
            card.addView(row)
            card.setOnClickListener { openEditor(a) }
            alarmList.addView(card, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 10)))
        }
    }

    /** アラームの登録・直す画面 */
    private fun openEditor(orig: Alarms.Alarm?) {
        val c = this
        val now = Calendar.getInstance()
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 20); setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 8))
        }

        // 時刻（くるくる回して選ぶ）
        box.addView(Ui.text(c, "時刻", 15f, Ui.SUB, bold = true))
        val timeRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val hp = NumberPicker(c).apply {
            minValue = 0; maxValue = 23; setFormatter { "%02d".format(it) }
            value = orig?.hour ?: now.get(Calendar.HOUR_OF_DAY)
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val mp = NumberPicker(c).apply {
            minValue = 0; maxValue = 59; setFormatter { "%02d".format(it) }
            value = orig?.minute ?: ((now.get(Calendar.MINUTE) / 5 + 1) * 5 % 60)
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        timeRow.addView(hp)
        timeRow.addView(Ui.text(c, "：", 30f, bold = true))
        timeRow.addView(mp)
        box.addView(timeRow)

        // 曜日
        box.addView(Ui.text(c, "くり返す曜日（選ばない＝1回だけ）", 15f, Ui.SUB, bold = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 14)))
        var days = orig?.days ?: 0
        val dayBtns = ArrayList<Button>()
        val daysLabel = Ui.text(c, "", 14f, Ui.WORK, bold = true)
        fun paintDays() {
            dayBtns.forEachIndexed { i, b ->
                val sel = (days and (1 shl i)) != 0
                b.background = Ui.round(if (sel) Ui.WORK else Ui.CHIP, 12, c)
                b.setTextColor(if (sel) 0xFFFFFFFF.toInt() else Ui.INK)
            }
            daysLabel.text = "→ " + Alarms.daysText(days)
        }
        val dayRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..6) {
            val b = Ui.button(c, Alarms.DAY_NAMES[i].toString(), Ui.CHIP, Ui.INK, 15f, 44) {
                days = days xor (1 shl i); paintDays()
            }
            b.setPadding(0, 0, 0, 0)
            dayBtns.add(b)
            dayRow.addView(b, Ui.lp(0, WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 4) })
        }
        box.addView(dayRow, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 6)))
        val quick = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("平日" to Alarms.WEEKDAYS, "毎日" to Alarms.EVERYDAY, "1回だけ" to 0).forEachIndexed { i, (name, v) ->
            quick.addView(Ui.button(c, name, 0xFFF3EFE9.toInt(), Ui.INK, 14f, 40) { days = v; paintDays() },
                Ui.lp(0, WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 6) })
        }
        box.addView(quick, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 6)))
        box.addView(daysLabel, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 4)))
        paintDays()

        // メモ
        box.addView(Ui.text(c, "メモ（鳴ったときに表示）", 15f, Ui.SUB, bold = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 14)))
        val memo = EditText(c).apply {
            hint = "例：会議の準備、生成AIパスポートの勉強"
            setText(orig?.memo ?: "")
            textSize = 17f
            isSingleLine = true
        }
        box.addView(memo, Ui.lp(MATCH_PARENT, WRAP_CONTENT))

        // スヌーズの時間
        box.addView(Ui.text(c, "スヌーズ（もう一度鳴るまでの時間）", 15f, Ui.SUB, bold = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 14)))
        var snz = orig?.snooze ?: Alarms.SNOOZE_MIN
        val snzBtns = ArrayList<Pair<Int, Button>>()
        fun paintSnz() {
            snzBtns.forEach { (v, b) ->
                val sel = v == snz
                b.background = Ui.round(if (sel) Ui.WORK else Ui.CHIP, 12, c)
                b.setTextColor(if (sel) 0xFFFFFFFF.toInt() else Ui.INK)
            }
        }
        val snzRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        Alarms.SNOOZE_CHOICES.forEachIndexed { i, v ->
            val b = Ui.button(c, "${v}分", Ui.CHIP, Ui.INK, 15f, 44) { snz = v; paintSnz() }
            snzBtns.add(v to b)
            snzRow.addView(b, Ui.lp(0, WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = Ui.dp(c, 6) })
        }
        box.addView(snzRow, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 6)))
        paintSnz()

        // 音
        box.addView(Ui.text(c, "音", 15f, Ui.SUB, bold = true),
            Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 14)))
        editSound = orig?.sound ?: ""
        val soundLabel = Ui.text(c, Alarms.soundTitle(c, editSound), 17f, bold = true)
        editSoundLabel = soundLabel
        box.addView(soundLabel, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 2)))
        val soundRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        soundRow.addView(Ui.button(c, "音を選ぶ", Ui.INK, 0xFFFFFFFF.toInt(), 15f, 46) { pickSound() },
            Ui.lp(0, WRAP_CONTENT, 1f))
        soundRow.addView(Ui.button(c, "試しに鳴らす", 0xFFF3EFE9.toInt(), Ui.INK, 15f, 46) { playPreview(editSound) },
            Ui.lp(0, WRAP_CONTENT, 1f).apply { leftMargin = Ui.dp(c, 8) })
        box.addView(soundRow, Ui.lp(MATCH_PARENT, WRAP_CONTENT, top = Ui.dp(c, 6)))

        val dlg = AlertDialog.Builder(c)
            .setTitle(if (orig == null) "アラームを追加" else "アラームを直す")
            .setView(ScrollView(c).apply { addView(box) })
            .setPositiveButton("保存") { _, _ ->
                stopPreview()
                val a = Alarms.Alarm(
                    id = orig?.id ?: Alarms.newId(c),
                    hour = hp.value, minute = mp.value, days = days,
                    memo = memo.text.toString().trim(), sound = editSound, enabled = true,
                    snooze = snz)
                Alarms.upsert(c, a)
                toast(Alarms.untilText(a))
                renderAlarms()
            }
            .setNegativeButton("やめる") { _, _ -> stopPreview() }
        if (orig != null) {
            dlg.setNeutralButton("削除") { _, _ ->
                stopPreview()
                AlertDialog.Builder(c)
                    .setMessage("${Alarms.timeText(orig)} のアラームを削除しますか？")
                    .setPositiveButton("削除する") { _, _ -> Alarms.delete(c, orig.id); renderAlarms() }
                    .setNegativeButton("やめる", null)
                    .show()
            }
        }
        dlg.setOnDismissListener { editSoundLabel = null }
        dlg.show()
    }

    private fun pickSound() {
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "アラームの音を選ぶ")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                if (editSound.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(editSound))
        try { startActivityForResult(i, PICK_SOUND) } catch (e: Exception) { toast("音の一覧を開けませんでした") }
    }

    @Deprecated("Activity の古い受け取り方（このアプリでは問題なし）")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SOUND || resultCode != RESULT_OK || data == null) return
        @Suppress("DEPRECATION")
        val uri = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        editSound = if (uri == null || uri == def) "" else uri.toString()
        editSoundLabel?.text = Alarms.soundTitle(this, editSound)
    }

    private fun playPreview(sound: String) {
        stopPreview()
        try {
            val r = RingtoneManager.getRingtone(this, Alarms.previewUri(sound)) ?: return
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            r.play()
            preview = r
            handler.postDelayed({ stopPreview() }, 4000)
        } catch (e: Exception) { toast("音を鳴らせませんでした") }
    }

    private fun stopPreview() {
        try { preview?.stop() } catch (e: Exception) { }
        preview = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ================= 許可 =================
    private fun notificationsAllowed(): Boolean = Timer.nm(this).areNotificationsEnabled()

    private fun permissionProblem(): String? = when {
        !notificationsAllowed() ->
            "⚠ お知らせ（通知）が許可されていません。\n下のボタンから「通知を許可」をオンにしてください。"
        !Timer.canOverlay(this) ->
            "⚠ YouTubeなどを見ているときに、画面いっぱいの「止める」画面を出すには許可が必要です。\n下のボタンから「ひと息タイマー」を探して、「他のアプリの上に表示」（または「最前面に表示」）をオンにしてください。"
        !Timer.canFullScreen(this) ->
            "⚠ 画面が消えているときの全画面表示が許可されていません。\n下のボタンから「全画面通知」を許可してください。"
        else -> null
    }

    private fun openPermSettings() {
        val i = if (!notificationsAllowed()) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else if (!Timer.canOverlay(this)) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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
