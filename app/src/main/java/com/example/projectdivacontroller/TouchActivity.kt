package com.example.projectdivacontroller

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import androidx.core.graphics.toColorInt
import kotlin.math.absoluteValue

class TouchActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var tcpClient: TcpClient? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sliderHeightRatio = 0
    // 用來記錄上一次各 pointer 的狀態
    private val lastTouchStates = mutableMapOf<Int, Int>() // id -> (action, x)

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch)

        // 🔹 沉浸模式設定
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        statusText = findViewById(R.id.statusText)
        val touchArea = findViewById<View>(R.id.touchArea)

        val ip = intent.getStringExtra("ip") ?: return
        val port = intent.getIntExtra("port", 0)
        sliderHeightRatio = intent.getIntExtra("sliderHeightRatio",0)

        tcpClient = TcpClient(ip, port) {
            // 若連線中斷，自動返回主畫面
            runOnUiThread {
                statusText.text = "Disconnected"
                finishWithResult(RESULT_DISCONNECTED)
            }
        }

        // 🔹 嘗試連線
        scope.launch(Dispatchers.IO) {
            if (tcpClient?.connect() == true) {
                sendScreenInfo()
                runOnUiThread {
                    showFourSectionsWithYellowTop()
                }
            } else {
                runOnUiThread {
                    statusText.text = "Connecting...failed"
                    finishWithResult(RESULT_CONNECT_FAILED)
                }
            }
        }


        touchArea.setOnTouchListener { _, event ->
            val msg: String? =
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                        val index =
                            if (event.actionMasked == MotionEvent.ACTION_DOWN)
                                0
                            else
                                event.actionIndex
                        val id = event.getPointerId(index)
                        val x = event.getX(index).toInt()
                        lastTouchStates.put(id, x)
                        "D $id $x ${event.getY(index).toInt()}\n"
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        val index =
                            if (event.actionMasked == MotionEvent.ACTION_UP)
                                0
                            else
                                event.actionIndex
                        val id = event.getPointerId(index)
                        lastTouchStates.remove(id)
                        "U $id\n"
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        lastTouchStates.clear()
                        "C\n"
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val sb = StringBuilder()
                        sb.append("M")
                        val pointerCount = event.pointerCount
                        for (i in 0 until pointerCount) {
                            val id = event.getPointerId(i)
                            val x = event.getX(i).toInt()
                            if ((lastTouchStates[id]!! - x).absoluteValue > 4) {
                                lastTouchStates.set(id, x)
                                sb.append(" $id $x ${event.getY(i).toInt()}")
                            }
                        }
                        if (sb.length < 4)
                            null
                        else {
                            sb.append("\n")
                            sb.toString()
                        }
                    }

                    else -> null
                }

            if (msg!=null) {
                tcpClient?.send(msg)
            }

            true
        }

    }
    private fun showFourSectionsWithYellowTop() {
        val root = findViewById<FrameLayout>(R.id.touchArea)
        root.removeAllViews()

        val colors = listOf("#00DDAA", "#FF66DD", "#44AAFF", "#FF2277")
        val images = listOf(
            R.drawable.triangle_v,
            R.drawable.square_v,
            R.drawable.cross_v,
            R.drawable.circle_v
        )

        // 1️⃣ 黃色區塊
        val yellowView = View(this).apply {
            setBackgroundColor("#FEFF00".toColorInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0 // 高度先設 0，後面 post 計算
            )
        }


        // 2️⃣ 四等分水平 LinearLayout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        for (i in 0 until 4) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setBackgroundColor(colors[i].toColorInt())
                setImageResource(images[i])
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            layout.addView(iv)
        }

        root.addView(layout)
        root.addView(yellowView)
        // 3️⃣ 計算高度
        root.post {
            val yellowHeight = (root.height * sliderHeightRatio / 100)

            // 設定黃色高度
            yellowView.layoutParams.height = yellowHeight
            yellowView.requestLayout()

            // 讓四等分 layout 下移
            val lp = layout.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = yellowHeight
            layout.layoutParams = lp
        }

    }


    private fun sendScreenInfo() {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val displayMetrics = DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val xdpi = displayMetrics.xdpi
        val ydpi = displayMetrics.ydpi
        val manufacturer = Build.MANUFACTURER   // 例如 "Samsung"
        val model = Build.MODEL                 // 例如 "SM-G9980"
        val msg = "SCREEN: $width $height $xdpi $ydpi $sliderHeightRatio $manufacturer $model\n"
        tcpClient?.send(msg)
    }


    private fun finishWithResult(result: Int) {
        setResult(result)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpClient?.close()
        scope.cancel()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showFourSectionsWithYellowTop()
        sendScreenInfo()
    }
}
