package jp.jamsesion.hitoiki

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 画面の部品を作るための小さな道具 */
object Ui {
    const val BG = 0xFFF6F3EE.toInt()
    const val INK = 0xFF2B2A28.toInt()
    const val SUB = 0xFF77736C.toInt()
    const val WORK = 0xFFD9603B.toInt()
    const val WORK_SOFT = 0xFFF7E3DA.toInt()
    const val REST = 0xFF3F8F6B.toInt()
    const val REST_SOFT = 0xFFDCEFE5.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val CHIP = 0xFFEFEBE4.toInt()
    const val WARN = 0xFFFFF1D6.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun round(color: Int, radiusDp: Int, ctx: Context): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(ctx, radiusDp).toFloat() }

    fun text(ctx: Context, s: String, sp: Float, color: Int = INK, bold: Boolean = false,
             center: Boolean = false): TextView = TextView(ctx).apply {
        text = s; textSize = sp; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (center) gravity = Gravity.CENTER
    }

    fun button(ctx: Context, s: String, bg: Int, fg: Int, sp: Float = 19f,
               heightDp: Int = 60, onClick: () -> Unit): Button = Button(ctx).apply {
        text = s; textSize = sp; setTextColor(fg); isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = round(bg, 16, ctx)
        stateListAnimator = null
        minHeight = dp(ctx, heightDp); minimumHeight = dp(ctx, heightDp)
        setOnClickListener { onClick() }
    }

    fun lp(w: Int, h: Int, weight: Float = 0f, top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top }

    fun card(ctx: Context, color: Int = CARD): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = round(color, 18, ctx)
        val p = dp(ctx, 16); setPadding(p, p, p, p)
    }
}
