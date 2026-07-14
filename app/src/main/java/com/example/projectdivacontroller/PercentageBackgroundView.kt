package com.example.projectdivacontroller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.graphics.Point
import android.os.SystemClock
import android.view.WindowManager

class PercentageBackgroundView(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var coverRatio = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    private val yellowPaint = Paint().apply {
        color = 0xFFFFF9E0.toInt() // 淡黃色
        isAntiAlias = false
    }

    private val whitePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = false
    }
    private val blackPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
    }
    private val screenRealSize = Point().apply {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealSize(this)
        //目前，旋轉會導致重建MainActivity，因此getRealSize一次就夠了
    }

    private val location = IntArray(2)
    private var time1 = 0L
    private var time2 = 0L

    var vx1: Float? = null
        set(value) {
            field = value
            showMove1()
        }
    var vx2: Float? = null
        set(value) {
            field = value
            showMove2()
        }
    var drawLine1 = false
        set(value) {
            field = value
            invalidate()
        }
    var drawLine2 = false
        set(value) {
            field = value
            invalidate()
        }
    var lineSpacing1: Float? = null
        set(value) {
            field = value
            invalidate()
        }
    var lineSpacing2: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fullScreenHeight = screenRealSize.y

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

        val now = SystemClock.uptimeMillis()

        val dx1 = if (vx1 != null) {
            (now - time1) * vx1!!
        } else 9999999f
        if (drawLine1 || dx1 < width + (vx1 ?: 0f) * 1024f) {
            val spacing = lineSpacing1
            if (spacing != null && spacing >= 2f) {
                var x = spacing
                while (x <= width) {
                    canvas.drawLine(
                        x,
                        0f,
                        x,
                        yellowHeight,
                        blackPaint
                    )
                    x += spacing
                }
            }
            if (dx1 < width) {
                canvas.drawRect(dx1, 0f, dx1 + 39f, yellowHeight, blackPaint)
                invalidate()
            } else if (!drawLine1) {
                postInvalidateDelayed(1039)
            }
        }

        val dx2 = if (vx2 != null) {
            (now - time2) * vx2!!
        } else 9999999f
        if (drawLine2 || dx2 < width + (vx2 ?: 0f) * 1024f) {
            val spacing = lineSpacing2
            if (spacing != null && spacing >= 2f) {
                var x = spacing
                while (x <= width) {
                    canvas.drawLine(
                        x,
                        yellowHeight,
                        x,
                        height.toFloat(),
                        blackPaint
                    )
                    x += spacing
                }
            }
            if (dx2 < width) {
                canvas.drawRect(dx2, yellowHeight, dx2 + 39f, height.toFloat(), blackPaint)
                invalidate()
            } else if (!drawLine2) {
                postInvalidateDelayed(1039)
            }
        }
    }

    fun showMove1() {
        time1 = SystemClock.uptimeMillis()
        invalidate()
    }

    fun showMove2() {
        time2 = SystemClock.uptimeMillis()
        invalidate()
    }
}