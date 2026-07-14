package com.example.projectdivacontroller

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
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

class TouchActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var tcpClient: TcpClient? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sliderHeightRatio = 0

    private lateinit var divaController: DivaController

    private data class LaneStyle(
        val normalColor: Int,
        val fullColor: Int,
        val melodyIcon: Drawable,
        val melodyIconSync: Drawable
    )

    private lateinit var laneStyles: Array<LaneStyle>

    private lateinit var slideIconLeft: Drawable
    private lateinit var slideIconLeftSync: Drawable
    private lateinit var slideIconRight: Drawable
    private lateinit var slideIconRightSync: Drawable

    private lateinit var buttonViews: Array<ImageView>
    private lateinit var sliderView: Array<ImageView>

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch)

        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder(StrictMode.getThreadPolicy())
                .permitNetwork()
                .build()
        )

        // 🔹 沉浸模式設定
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        statusText = findViewById(R.id.statusText)
        val touchArea = findViewById<View>(R.id.touchArea)

        val arg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {// the new getXXX(key, class) APIs are buggy in Android 13
            intent.getParcelableExtra("DivaArgs", DivaArgs::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<DivaArgs>("DivaArgs")
        }!!
        sliderHeightRatio = arg.sliderHeightRatio

        divaController = DivaController(
            sliderHeightRatio,
            arg.sliderRequire1,
            arg.sliderRequire2,
            arg.energyDecayRate1,
            arg.energyDecayRate2
        )

        tcpClient = TcpClient(arg.ip, arg.port) {
            // 若連線中斷，自動返回主畫面
            runOnUiThread {
                statusText.text = "Disconnected"
                finishWithResult(RESULT_DISCONNECTED)
            }
        }

        // 🔹 嘗試連線
        scope.launch(Dispatchers.IO) {
            if (tcpClient?.connect() == true) {
                withContext(Dispatchers.Main) {
                    initializeResources()
                    initializeTouchArea()
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusText.text = "Connecting...failed"
                    finishWithResult(RESULT_CONNECT_FAILED)
                }
            }
        }

        touchArea.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val index =
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            divaController.lastUpdate = event.eventTime
                            0
                        } else
                            event.actionIndex
                    divaController.onPointerDown(
                        event.getPointerId(index),
                        event.getX(index),
                        event.getY(index),
                        event.eventTime
                    )
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val index =
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            0
                        } else
                            event.actionIndex
                    divaController.onPointerUp(
                        event.getPointerId(index),
                        event.eventTime
                    )
                }

                MotionEvent.ACTION_CANCEL -> {
                    divaController.reset()
                }

                MotionEvent.ACTION_MOVE -> {
                    val pointerCount = event.pointerCount
                    for (i in 0 until pointerCount) {
                        divaController.onPointerMove(
                            event.getPointerId(i),
                            event.getX(i),
                            event.getY(i),
                            event.eventTime
                        )
                    }
                    divaController.lastUpdate = event.eventTime
                }
            }
            if (divaController.keybdOutput.position() > 0) {
                tcpClient?.send(divaController.keybdOutput)
                divaController.keybdOutput.clear()
                updateViewContent()
            }
            true
        }
    }

    private fun initializeResources() {
        laneStyles = arrayOf(
            LaneStyle(
                0xD800DDAA.toInt(),
                0xFF00DDAA.toInt(),
                getDrawable(
                    R.drawable.triangle_v
                )!!,
                getDrawable(
                    R.drawable.triangle_sync_v
                )!!
            ),
            LaneStyle(
                0xD8FF66DD.toInt(),
                0xFFFF66DD.toInt(),
                getDrawable(
                    R.drawable.square_v
                )!!,
                getDrawable(
                    R.drawable.square_sync_v
                )!!
            ),
            LaneStyle(
                0xD844AAFF.toInt(),
                0xFF44AAFF.toInt(),
                getDrawable(
                    R.drawable.cross_v
                )!!,
                getDrawable(
                    R.drawable.cross_sync_v
                )!!
            ),
            LaneStyle(
                0xD8FF2277.toInt(),
                0xFFFF2277.toInt(),
                getDrawable(
                    R.drawable.circle_v
                )!!,
                getDrawable(
                    R.drawable.circle_sync_v
                )!!
            )
        )

        slideIconLeft =
            getDrawable(
                R.drawable.slide_left
            )!!
        slideIconLeftSync =
            getDrawable(
                R.drawable.slide_left_sync
            )!!
        slideIconRight =
            getDrawable(
                R.drawable.slide_right
            )!!
        slideIconRightSync =
            getDrawable(
                R.drawable.slide_right_sync
            )!!
    }

    private fun createImageView(): ImageView =
        ImageView(this).apply {

            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )

            scaleType = ImageView.ScaleType.FIT_CENTER

            adjustViewBounds = true
        }

    private fun initializeTouchArea() {

        val root = findViewById<FrameLayout>(R.id.touchArea)

        root.removeAllViews()

        val sliderLayout = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL

            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
        }

        sliderView = Array(2) {

            createImageView().also {

                it.setBackgroundColor(0xD8FEFF00.toInt())

                sliderLayout.addView(it)
            }
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
        }

        buttonViews = Array(4) {
            createImageView().also(
                buttonLayout::addView
            )
        }

        root.addView(buttonLayout)
        root.addView(sliderLayout)

        root.post {
            val sliderHeight = root.height * sliderHeightRatio / 100
            (sliderLayout.layoutParams
                    as FrameLayout.LayoutParams)
                .apply {
                    bottomMargin = root.height - sliderHeight
                }
            sliderLayout.requestLayout()
            (buttonLayout.layoutParams
                    as FrameLayout.LayoutParams)
                .apply {
                    topMargin = sliderHeight
                }
            buttonLayout.requestLayout()

            divaController.setSize(root.width, root.height)
        }
        updateViewContent()
    }

    private fun updateViewContent() {

        val buttons = divaController.keybdState.buttons

        for (lane in 0 until 4) {

            val primary = buttons[lane]

            val secondary = buttons[lane + 4]

            val style = laneStyles[lane]

            buttonViews[lane].setBackgroundColor(
                if (primary && secondary)
                    style.fullColor
                else
                    style.normalColor
            )

            buttonViews[lane].setImageDrawable(
                if (primary || secondary)
                    style.melodyIconSync
                else
                    style.melodyIcon
            )
        }
        if (divaController.keybdState.sticks[0] != 0 && divaController.keybdState.sticks[1] != 0) {
            sliderView[0].setImageDrawable(
                if (divaController.keybdState.sticks[0] > 0) slideIconRightSync
                else slideIconLeftSync
            )
            sliderView[1].setImageDrawable(
                if (divaController.keybdState.sticks[1] > 0) slideIconRightSync
                else slideIconLeftSync
            )
        } else {
            sliderView[0].setImageDrawable(
                when (divaController.keybdState.sticks[0]) {
                    0 -> null
                    1 -> slideIconRight
                    else -> slideIconLeft
                }
            )
            sliderView[1].setImageDrawable(
                when (divaController.keybdState.sticks[1]) {
                    0 -> null
                    2 -> slideIconRight
                    else -> slideIconLeft
                }
            )
        }
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
        initializeTouchArea()
    }
}
