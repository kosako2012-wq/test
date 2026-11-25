package jp.jamsesion.hitoiki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 予約した時刻が来たとき（タイマー） */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.onAlarm(ctx)
    }
}

/** 「OK」を押したとき：音を止める（タイマー） */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.nm(ctx).cancel(Timer.ID_ALERT)
    }
}

/** アラームの時刻が来たとき */
class AlarmFireReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.onFire(ctx, intent.getIntExtra("id", 0), intent.getBooleanExtra("snooze", false))
    }
}

/** アラームのお知らせの「止める」「5分後にもう一度」を押したとき */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        if (intent.getStringExtra("act") == "snooze") Alarms.snooze(ctx, id) else Alarms.stopRing(ctx, id)
        AlarmRingActivity.showing?.finish()
    }
}

/** 電源を入れ直したとき・アプリを更新したとき：予約をかけ直す */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.restore(ctx)
        Alarms.restore(ctx)
    }
}
