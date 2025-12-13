package com.example.projectdivacontroller
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.graphics.Point
import android.view.WindowManager
import androidx.core.graphics.toColorInt

class PercentageBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var coverRatio = 0f

    private val yellowPaint = Paint().apply {
        color = "#FFF9E0".toColorInt() // 淡黃色
    }

    private val whitePaint = Paint().apply {
        color = Color.WHITE
    }
    private val size = Point()
    private val location = IntArray(2)
    fun setCoverRatio(ratio: Float) {
        coverRatio = ratio.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealSize(size)
        val fullScreenHeight = size.y

        val w = width.toFloat()
        val h = height.toFloat()

        // 2️⃣ 取得 View 在螢幕上的位置
        getLocationOnScreen(location)
        val viewTopOnScreen = location[1].toFloat()

        // 3️⃣ 計算黃色高度
        val yellowHeight = (fullScreenHeight * coverRatio - viewTopOnScreen).coerceAtLeast(0f)

        // 上方黃色區
        canvas.drawRect(0f, 0f, w, yellowHeight.coerceAtMost(h), yellowPaint)

        // 下方白色區
        canvas.drawRect(0f, yellowHeight, w, h, whitePaint)
    }

}