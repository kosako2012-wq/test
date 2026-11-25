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
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * アラームの中身（登録・予約・鳴らす・スヌーズ）をまとめたもの。
 * 目覚まし時計と同じ方法で予約するので、画面が消えていても時間どおりに鳴ります。
 */
object Alarms {
    private const val PREFS = "alarms"
    const val SNOOZE_MIN = 5
    const val RING_LIMIT_MS = 3 * 60_000L   // 押さなくても3分で止める
    const val DAY_NAMES = "月火水木金土日"
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val EVERYDAY = 0b1111111

    /** days：月=1, 火=2, 水=4 … 日=64 の合計。0 は「1回だけ」 */
    data class Alarm(
        val id: Int, val hour: Int, val minute: Int, val days: Int,
        val memo: String, val sound: String, val enabled: Boolean
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- 保存 ----------
    fun list(ctx: Context): List<Alarm> {
        val raw = prefs(ctx).getString("list", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<Alarm>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(Alarm(o.optInt("id"), o.optInt("h"), o.optInt("m"), o.optInt("d"),
                o.optString("memo"), o.optString("sound"), o.optBoolean("on", true)))
        }
        return out.sortedWith(compareBy<Alarm>({ it.hour }, { it.minute }))
    }

    private fun saveAll(ctx: Context, items: List<Alarm>) {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("h", a.hour).put("m", a.minute)
                .put("d", a.days).put("memo", a.memo).put("sound", a.sound).put("on", a.enabled))
        }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
        cleanChannels(ctx, items)
    }

    fun get(ctx: Context, id: Int): Alarm? = list(ctx).firstOrNull { it.id == id }
    fun newId(ctx: Context): Int = (list(ctx).maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(ctx: Context, a: Alarm) {
        saveAll(ctx, list(ctx).filter { it.id != a.id } + a)
        cancelSnooze(ctx, a.id)
        schedule(ctx, a)
    }

    fun delete(ctx: Context, id: Int) {
        am(ctx).cancel(firePI(ctx, id, false))
        cancelSnooze(ctx, id)
        stopRing(ctx, id)
        saveAll(ctx, list(ctx).filter { it.id != id })
    }

    fun setEnabled(ctx: Context, id: Int, on: Boolean) {
        val a = get(ctx, id) ?: return
        upsert(ctx, a.copy(enabled = on))
    }

    // ---------- 次に鳴る時刻 ----------
    fun nextTime(a: Alarm, from: Long = System.currentTimeMillis()): Long {
        val base = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            val c = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            if (c.timeInMillis <= from) continue
            if (a.days == 0 || (a.days and (1 shl dayIndex(c))) != 0) return c.timeInMillis
        }
        return base.timeInMillis + 86_400_000L
    }

    /** 月=0 … 日=6 */
    private fun dayIndex(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

    // ---------- 予約 ----------
    private fun am(ctx: Context): AlarmManager = ctx.getSystemService(AlarmManager::class.java)

    private fun firePI(ctx: Context, id: Int, snooze: Boolean): PendingIntent =
        PendingIntent.getBroadcast(ctx, (if (snooze) 3000 else 1000) + id,
            Intent(ctx, AlarmFireReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openPI(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 900,
            Intent(ctx, MainActivity::class.java).putExtra("tab", "alarm")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setAt(ctx: Context, at: Long, pi: PendingIntent) {
        try {
            am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, openPI(ctx)), pi)
        } catch (e: SecurityException) {
            am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun schedule(ctx: Context, a: Alarm) {
        am(ctx).cancel(firePI(ctx, a.id, false))
        if (a.enabled) setAt(ctx, nextTime(a), firePI(ctx, a.id, false))
    }

    private fun cancelSnooze(ctx: Context, id: Int) {
        am(ctx).cancel(firePI(ctx, id, true))
        prefs(ctx).edit().remove("snooze_$id").apply()
    }

    fun snoozeAt(ctx: Context, id: Int): Long = prefs(ctx).getLong("snooze_$id", 0L)

    /** 電源を入れ直したとき・アプリを更新したとき */
    fun restore(ctx: Context) {
        val now = System.currentTimeMillis()
        list(ctx).forEach { a ->
            schedule(ctx, a)
            val s = snoozeAt(ctx, a.id)
            if (s > now) setAt(ctx, s, firePI(ctx, a.id, true))
            else if (s > 0) prefs(ctx).edit().remove("snooze_${a.id}").apply()
        }
    }

    // ---------- 鳴らす ----------
    /** 予約した時刻が来たとき（AlarmFireReceiverから呼ばれる） */
    fun onFire(ctx: Context, id: Int, snooze: Boolean) {
        val a = get(ctx, id) ?: return
        if (snooze) {
            prefs(ctx).edit().remove("snooze_$id").apply()
        } else {
            if (!a.enabled) return
            if (a.days == 0) {
                // 1回だけのアラームは、鳴ったらオフにする
                saveAll(ctx, list(ctx).map { if (it.id == id) it.copy(enabled = false) else it })
            } else {
                schedule(ctx, a)   // 次のくり返しを予約
            }
        }
        ring(ctx, a)
    }

    private fun notifId(id: Int) = 2000 + id

    fun ring(ctx: Context, a: Alarm) {
        val ch = soundChannel(ctx, a.sound)
        val title = "⏰ " + timeText(a)
        val text = a.memo.ifBlank { "アラームの時間です" }
        val ringIntent = Intent(ctx, AlarmRingActivity::class.java).putExtra("id", a.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val full = PendingIntent.getActivity(ctx, 4000 + a.id, ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopPI = PendingIntent.getBroadcast(ctx, 5000 + a.id,
            Intent(ctx, AlarmActionReceiver::class.java).putExtra("id", a.id).putExtra("act", "stop"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snzPI = PendingIntent.getBroadcast(ctx, 6000 + a.id,
            Intent(ctx, AlarmActionReceiver::class.java).putExtra("id", a.id).putExtra("act", "snooze"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = Notification.Builder(ctx, ch)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setAutoCancel(true)
            .setTimeoutAfter(RING_LIMIT_MS)
            .setColor(Ui.WORK)
            .addAction(Notification.Action.Builder(null, "止める", stopPI).build())
            .addAction(Notification.Action.Builder(null, "${SNOOZE_MIN}分後にもう一度", snzPI).build())
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT   // 止めるまで鳴り続ける（最長3分）
        Timer.nm(ctx).notify(notifId(a.id), n)

        // YouTubeなどを見ていても、画面いっぱいのアラーム画面を出す
        if (Timer.canOverlay(ctx)) {
            try { ctx.startActivity(ringIntent) } catch (e: Exception) { }
        }
    }

    fun stopRing(ctx: Context, id: Int) {
        Timer.nm(ctx).cancel(notifId(id))
    }

    fun snooze(ctx: Context, id: Int) {
        stopRing(ctx, id)
        val at = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        prefs(ctx).edit().putLong("snooze_$id", at).apply()
        setAt(ctx, at, firePI(ctx, id, true))
    }

    // ---------- 音 ----------
    private fun soundUri(sound: String): Uri =
        if (sound.isBlank())
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else Uri.parse(sound)

    fun previewUri(sound: String): Uri = soundUri(sound)

    private fun channelId(sound: String) = "alarm_" + Integer.toHexString(sound.hashCode()) + "_v1"

    /** 音ごとにお知らせの種類（チャンネル）を分ける。アラームの音量で鳴らす */
    private fun soundChannel(ctx: Context, sound: String): String {
        val id = channelId(sound)
        val m = Timer.nm(ctx)
        if (m.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "アラーム（${soundTitle(ctx, sound)}）",
                NotificationManager.IMPORTANCE_HIGH)
            ch.description = "アラームの時間に、選んだ音と振動で知らせます"
            ch.setSound(soundUri(sound), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            m.createNotificationChannel(ch)
        }
        return id
    }

    /** 使われなくなった音のチャンネルを片付ける */
    private fun cleanChannels(ctx: Context, items: List<Alarm>) {
        val used = items.map { channelId(it.sound) }.toSet()
        val m = Timer.nm(ctx)
        m.notificationChannels.filter { it.id.startsWith("alarm_") && it.id !in used }
            .forEach { m.deleteNotificationChannel(it.id) }
    }

    fun soundTitle(ctx: Context, sound: String): String {
        if (sound.isBlank()) return "標準のアラーム音"
        return try {
            RingtoneManager.getRingtone(ctx, Uri.parse(sound))?.getTitle(ctx) ?: "選んだ音"
        } catch (e: Exception) { "選んだ音" }
    }

    // ---------- 表示用の言葉 ----------
    fun timeText(a: Alarm): String = "%02d:%02d".format(a.hour, a.minute)

    fun daysText(days: Int): String = when (days) {
        0 -> "1回だけ"
        WEEKDAYS -> "平日（月〜金）"
        WEEKEND -> "土日"
        EVERYDAY -> "毎日"
        else -> (0..6).filter { (days and (1 shl it)) != 0 }.map { DAY_NAMES[it] }.joinToString("・")
    }

    /** 「今日 07:30」「明日 07:30」「11/28（土） 07:30」 */
    fun nextLabel(a: Alarm): String {
        val at = nextTime(a)
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        fun same(x: Calendar, y: Calendar) =
            x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
        val day = when {
            same(c, today) -> "今日"
            same(c, tomorrow) -> "明日"
            else -> "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}（${DAY_NAMES[dayIndex(c)]}）"
        }
        return "$day ${timeText(a)}"
    }

    /** 「あと3時間20分で鳴ります」 */
    fun untilText(a: Alarm): String {
        val min = ((nextTime(a) - System.currentTimeMillis()) / 60_000L).toInt() + 1
        val h = min / 60; val m = min % 60
        val s = when {
            h >= 24 -> "${h / 24}日${h % 24}時間"
            h > 0 -> "${h}時間${m}分"
            else -> "${m}分"
        }
        return "あと${s}で鳴ります"
    }
}
