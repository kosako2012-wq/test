package jp.jamsesion.hitoiki

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 予約した時刻が来たとき */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.onAlarm(ctx)
    }
}

/** 「OK」を押したとき：音を止める */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.nm(ctx).cancel(Timer.ID_ALERT)
    }
}

/** 電源を入れ直したとき・アプリを更新したとき：予約をかけ直す */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Timer.restore(ctx)
    }
}
