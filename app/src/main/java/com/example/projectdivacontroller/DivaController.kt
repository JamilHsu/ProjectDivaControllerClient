package com.example.projectdivacontroller

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class DivaController(
    val sliderHeightRatio: Int,
    val sliderRequire1: Float,
    val sliderRequire2: Float,
    val energyDecayRate1: Float,
    val energyDecayRate2: Float
) {
    data class PointerInfo(
        var x: Float = 0f,
        var y: Float = 0f,

        var inSliderOnly: Boolean = false,
        var slideEnergy: Float = 0.0f,
        // 0 = none
        // 1~8 = buttonIndex + 1
        var pressingButton: Int = 0,

        // -2,-1,0,1,2
        var pressingDirectionalButton: Int = 0,
    )

    var lastUpdate: Long = 0L

    class KeyboardState {
        val buttons = BooleanArray(8)

        // 上次主要按鍵抬起的時間，或 0 if上次抬起的不是主要按鍵。用於輪流按下按鍵避免在抬起按鍵的瞬間又馬上按下同一個按鍵
        val buttonUpTime = LongArray(4)

        // 0 / ±1 / ±2
        // 1是左邊；2是右邊；左負右正
        val sticks = IntArray(2)
    }

    val keybdState = KeyboardState()

    private val pointerCache = arrayOfNulls<PointerInfo>(16)

    private var width = 1f
    private var height = 1f
    private var sliderHeight = 0f
    val keybdOutput: ByteBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)

    fun setSize(
        width: Int,
        height: Int
    ) {
        this.width = max(width, 1).toFloat()
        this.height = max(height, 1).toFloat()

        sliderHeight = this.height * sliderHeightRatio / 100f
    }

    fun onPointerDown(
        pointerId: Int,
        x: Float,
        y: Float,
        timestamp: Long
    ) {

        if (pointerId !in pointerCache.indices) {
            return
        }

        if (pointerCache[pointerId] != null) {
            onPointerUp(
                pointerId,
                timestamp
            )
        }

        val info = PointerInfo(
            x = x,
            y = y,
        )

        pointerCache[pointerId] = info

        if (y < sliderHeight) {
            info.inSliderOnly = true
            return
        }

        var buttonIndex = (x * 4f / width).toInt().coerceIn(0, 3)

        if (
            keybdState.buttons[buttonIndex] &&
            keybdState.buttons[buttonIndex + 4]
        ) {
            return
        }

        if (keybdState.buttons[buttonIndex]
            || (!keybdState.buttons[buttonIndex + 4] && keybdState.buttonUpTime[buttonIndex] != 0L && timestamp - keybdState.buttonUpTime[buttonIndex] < 390) //ms
        ) {
            buttonIndex += 4
        }

        keybdState.buttons[buttonIndex] = true
        keybdOutput.put('D'.code.toByte()).put(buttonIndex.toByte())
        info.pressingButton = buttonIndex + 1
    }

    fun onPointerUp(
        pointerId: Int,
        timestamp: Long
    ) {

        if (pointerId !in pointerCache.indices) {
            return
        }

        val info =
            pointerCache[pointerId]
                ?: return

        if (info.pressingButton != 0) {

            val buttonIndex = info.pressingButton - 1

            keybdState.buttons[buttonIndex] = false
            keybdOutput.put('U'.code.toByte()).put(buttonIndex.toByte())

            if (buttonIndex < 4) {
                keybdState.buttonUpTime[buttonIndex] = timestamp
            } else {
                keybdState.buttonUpTime[buttonIndex - 4] = 0L
            }
        }

        if (info.pressingDirectionalButton != 0) {

            keybdState.sticks[abs(info.pressingDirectionalButton) - 1] = 0

            keybdOutput.put('u'.code.toByte()).put(info.pressingDirectionalButton.toByte())
        }

        pointerCache[pointerId] = null
    }

    fun onPointerMove(
        pointerId: Int,
        x: Float,
        y: Float,
        timestamp: Long
    ) {
        if (pointerId !in pointerCache.indices) {
            return
        }

        val dt = timestamp - lastUpdate
        val info =
            pointerCache[pointerId]
                ?: return

        val dx = x - info.x
        val dy = y - info.y
        info.x = x
        info.y = y

        val energyDecay = dt * if (info.inSliderOnly) energyDecayRate1 else energyDecayRate2
        val sliderRequire = if (info.inSliderOnly) sliderRequire1 else sliderRequire2

        info.slideEnergy = (
                (if (info.slideEnergy > 0f) {
                    max(info.slideEnergy - energyDecay, 0f)
                } else {
                    min(info.slideEnergy + energyDecay, 0f)
                })
                        +
                        (if (dx == 0.0f) {
                            0.0f
                        } else {
                            val t = dy / dx
                            dx * (1.0f + ln(sqrt(1 + t * t))) //hypot(x, y) / abs(dx) == sqrt(1+(y/x)^2)
                        })
                ).coerceIn(-sliderRequire, sliderRequire)

        if (info.pressingDirectionalButton != 0) {
            if (
                (dx > 0.0f) != (info.pressingDirectionalButton > 0)
                || dx == 0.0f
            ) {
                keybdState.sticks[abs(info.pressingDirectionalButton) - 1] = 0

                keybdOutput.put('u'.code.toByte()).put(info.pressingDirectionalButton.toByte())
                info.pressingDirectionalButton = 0
            }
        }

        if (info.pressingDirectionalButton != 0) {
            return
        }

        val stickId =
            when {
                (keybdState.sticks[0] == 0 && (x * 2 < width || keybdState.sticks[1] != 0)) -> 1
                keybdState.sticks[1] == 0 -> 2
                else -> return
            }

        if (abs(info.slideEnergy) >= sliderRequire) {

            val direction =
                if (info.slideEnergy > 0f)
                    stickId
                else
                    -stickId

            keybdState.sticks[stickId - 1] = direction

            info.pressingDirectionalButton = direction
            keybdOutput.put('d'.code.toByte()).put(direction.toByte())
        }
    }

    fun reset() {
        keybdState.buttons.fill(false)
        keybdState.buttonUpTime.fill(0)
        keybdState.sticks.fill(0)

        pointerCache.fill(null)

        keybdOutput.put('C'.code.toByte())
    }

}