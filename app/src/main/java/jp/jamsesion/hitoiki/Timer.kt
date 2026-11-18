package jp.jamsesion.hitoiki

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * タイマーの中身（状態の記録・アラームの予約・お知らせ）をまとめたもの。
 * 状態は端末に保存するので、アプリを閉じても動き続けます。
 */
object Timer {
    const val IDLE = "idle"
    const val WORK = "work"
    const val REST = "rest"

    private const val PREFS = "hitoiki"
    const val CH_ALERT = "alert_v1"      // 時間になったときの知らせ（音＋振動＋割り込み）
    const val CH_STATUS = "status_v1"    // 動いている間の残り時間表示（静か）
    const val ID_ALERT = 10
    const val ID_STATUS = 11
    const val ID_END = 12
    const val CH_END = "end_v1"          // 休憩おわり（音だけ）
    const val ALERT_LIMIT_MS = 60_000L   // OKを押さなくても1分で止める

    // ---------- 状態 ----------
    data class State(
        val phase: String, val running: Boolean, val endAt: Long,
        val remain: Long, val total: Long, val round: Int,
        val workMin: Int, val restMin: Int
    ) {
        fun left(now: Long = System.currentTimeMillis()): Long =
            if (running) maxOf(0L, endAt - now) else remain
    }

    fun load(ctx: Context): State {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val work = p.getInt("workMin", 25)
        val rest = p.getInt("restMin", 5)
        return State(
            phase = p.getString("phase", IDLE) ?: IDLE,
            running = p.getBoolean("running", false),
            endAt = p.getLong("endAt", 0L),
            remain = p.getLong("remain", work * 60_000L),
            total = p.getLong("total", work * 60_000L),
            round = p.getInt("round", 0),
            workMin = work, restMin = rest
        )
    }

    private fun save(ctx: Context, s: State) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("phase", s.phase).putBoolean("running", s.running)
            .putLong("endAt", s.endAt).putLong("remain", s.remain)
            .putLong("total", s.total).putInt("round", s.round)
            .putInt("workMin", s.workMin).putInt("restMin", s.restMin)
            .apply()
    }

    // ---------- 操作 ----------
    fun setMinutes(ctx: Context, work: Int, rest: Int) {
        val s = load(ctx)
        if (s.phase != IDLE) return
        val ms = work * 60_000L
        save(ctx, s.copy(workMin = work, restMin = rest, remain = ms, total = ms))
    }

    fun start(ctx: Context) {
        val s = load(ctx)
        val total = s.workMin * 60_000L
        val now = System.currentTimeMillis()
        val n = s.copy(phase = WORK, running = true, round = 1, total = total,
            remain = total, endAt = now + total)
        save(ctx, n); schedule(ctx, n.endAt); showStatus(ctx, n)
    }

    fun pause(ctx: Context) {
        val s = load(ctx)
        if (!s.running) return
        val n = s.copy(running = false, remain = s.left())
        save(ctx, n); cancelAlarm(ctx); showStatus(ctx, n)
    }

    fun resume(ctx: Context) {
        val s = load(ctx)
        if (s.running || s.phase == IDLE) return
        val n = s.copy(running = true, endAt = System.currentTimeMillis() + s.remain)
        save(ctx, n); schedule(ctx, n.endAt); showStatus(ctx, n)
    }

    fun reset(ctx: Context) {
        val s = load(ctx)
        val ms = s.workMin * 60_000L
        save(ctx, s.copy(phase = IDLE, running = false, round = 0, remain = ms, total = ms, endAt = 0))
        cancelAlarm(ctx)
        nm(ctx).cancel(ID_STATUS)
        nm(ctx).cancel(ID_ALERT)
    }

    /** 予約した時刻が来たとき（AlarmReceiverから呼ばれる）
     *  使う時間が終わった → 画面いっぱいの「止める」画面＋音で知らせ、休憩を始める
     *  休憩が終わった → 音だけで知らせて、自動で終了（くり返さない） */
    fun onAlarm(ctx: Context) {
        val s = load(ctx)
        if (!s.running || s.phase == IDLE) return
        if (s.phase == WORK) {
            val total = s.restMin * 60_000L
            val n = s.copy(phase = REST, total = total, remain = total,
                endAt = System.currentTimeMillis() + total)
            save(ctx, n)
            schedule(ctx, n.endAt)
            showAlert(ctx, true, n)
            showStatus(ctx, n)
        } else {
            val ms = s.workMin * 60_000L
            save(ctx, s.copy(phase = IDLE, running = false, round = 0, remain = ms, total = ms, endAt = 0))
            cancelAlarm(ctx)
            nm(ctx).cancel(ID_STATUS)
            showRestEnd(ctx)
        }
    }

    /** 休憩おわり：音だけで知らせる（画面は出さない） */
    private fun showRestEnd(ctx: Context) {
        ensureChannels(ctx)
        val n = Notification.Builder(ctx, CH_END)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("休憩おわり")
            .setContentText("タイマーは終了しました")
            .setContentIntent(openAppPI(ctx))
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_LIMIT_MS)
            .build()
        nm(ctx).notify(ID_END, n)
    }

    /** 再起動のあとに予約をかけ直す */
    fun restore(ctx: Context) {
        val s = load(ctx)
        if (!s.running || s.phase == IDLE) return
        if (s.endAt <= System.currentTimeMillis()) onAlarm(ctx)
        else { schedule(ctx, s.endAt); showStatus(ctx, s) }
    }

    // ---------- アラームの予約 ----------
    private fun alarmPI(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 1, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openAppPI(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            // 目覚まし時計と同じ方法。省電力中でも時間どおりに動きます
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAppPI(ctx)), alarmPI(ctx))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPI(ctx))
        }
    }

    private fun cancelAlarm(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmPI(ctx))
    }

    // ---------- お知らせ ----------
    fun nm(ctx: Context): NotificationManager = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels(ctx: Context) {
        val m = nm(ctx)
        if (m.getNotificationChannel(CH_ALERT) == null) {
            val ch = NotificationChannel(CH_ALERT, "時間のお知らせ", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "使う時間・休憩時間が終わったときに、音と振動で割り込んで知らせます"
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            // 「アラーム」の音量で鳴らす（普段の通知音とは別の音量）
            ch.setSound(uri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            m.createNotificationChannel(ch)
        }
        if (m.getNotificationChannel(CH_END) == null) {
            val ch = NotificationChannel(CH_END, "休憩おわりの音", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "休憩が終わったときに、音だけで知らせます（画面は出ません）"
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ch.setSound(uri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            ch.enableVibration(false)
            m.createNotificationChannel(ch)
        }
        if (m.getNotificationChannel(CH_STATUS) == null) {
            val ch = NotificationChannel(CH_STATUS, "残り時間の表示", NotificationManager.IMPORTANCE_LOW)
            ch.description = "タイマーが動いている間、残り時間を表示します（音は鳴りません）"
            ch.setShowBadge(false)
            m.createNotificationChannel(ch)
        }
    }

    fun showAlert(ctx: Context, toRest: Boolean, next: State?, test: Boolean = false) {
        ensureChannels(ctx)
        val title = when {
            test -> "お知らせのテスト"
            toRest -> "休憩の時間です"
            else -> "休憩おわり"
        }
        val text = when {
            test -> "この音・振動・表示で知らせます"
            toRest -> "画面から離れて、立ち上がりましょう（${next?.restMin ?: 0}分）"
            else -> "次の「使う時間」が始まりました（${next?.workMin ?: 0}分）"
        }
        val full = PendingIntent.getActivity(ctx, 3,
            Intent(ctx, AlertActivity::class.java)
                .putExtra("title", title).putExtra("text", text).putExtra("toRest", toRest)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ok = PendingIntent.getBroadcast(ctx, 4, Intent(ctx, DismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val b = Notification.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_LIMIT_MS)
            .setColor(if (toRest) 0xFF3F8F6B.toInt() else 0xFFD9603B.toInt())
            .addAction(Notification.Action.Builder(null, "OK（音を止める）", ok).build())
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT  // OKを押すまで鳴り続ける（最長1分）
        nm(ctx).notify(ID_ALERT, n)

        // 「ほかのアプリの上に表示」が許可されていれば、YouTubeなどを見ていても
        // 画面いっぱいの「止める」画面を出す
        if (!test && canOverlay(ctx)) {
            try {
                ctx.startActivity(Intent(ctx, AlertActivity::class.java)
                    .putExtra("title", title).putExtra("text", text).putExtra("toRest", toRest)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { }
        }
    }

    fun canOverlay(ctx: Context): Boolean = android.provider.Settings.canDrawOverlays(ctx)

    fun showStatus(ctx: Context, s: State) {
        ensureChannels(ctx)
        if (s.phase == IDLE) { nm(ctx).cancel(ID_STATUS); return }
        val label = if (s.phase == WORK) "使う時間" else "休憩中"
        val b = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(label)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPI(ctx))
        if (s.running) {
            b.setContentText("残り時間").setUsesChronometer(true)
                .setChronometerCountDown(true).setWhen(s.endAt).setShowWhen(true)
        } else {
            b.setContentText("一時停止中・残り ${fmt(s.remain)}").setShowWhen(false)
        }
        nm(ctx).notify(ID_STATUS, b.build())
    }

    fun fmt(ms: Long): String {
        val sec = ((ms + 999) / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(sec / 60, sec % 60)
    }

    fun canFullScreen(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) nm(ctx).canUseFullScreenIntent() else true
}
