package com.example.projectdivacontroller

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import java.net.*
import kotlin.math.max
import androidx.core.content.edit
import kotlinx.parcelize.Parcelize
import kotlin.time.Duration.Companion.milliseconds


const val RESULT_CONNECT_FAILED = 10000
const val RESULT_DISCONNECTED = 10001

@Parcelize
data class DivaArgs(
    val ip: String,
    val port: Int,
    val sliderHeightRatio: Int,
    val sliderRequire1: Float,
    val sliderRequire2: Float,
    val energyDecayRate1: Float,
    val energyDecayRate2: Float
) : Parcelable

@SuppressLint("SetTextI18n")
class MainActivity : ComponentActivity() {

    private val udpPort = 39831
    private val broadcastMessage = "Miku Miku where are you?" //其實這個訊息是什麼都可以，目前根本沒檢查訊息的內容

    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var editSliderRequire1: EditText
    private lateinit var editSliderRequire2: EditText
    private lateinit var editEnergyDecayRate1: EditText
    private lateinit var editEnergyDecayRate2: EditText
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var btnSave: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtOtherMsg: TextView
    private lateinit var sliderHeightRatio: SeekBar
    private lateinit var txtSliderHeight: TextView
    private lateinit var backgroundView: PercentageBackgroundView
    private lateinit var displayManager: DisplayManager
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            checkRefreshRate()
        }
    }
    private var scanning = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 登錄新的 Activity 結果接收器
    private val touchActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        showMessage(
            when (result.resultCode) {
                RESULT_DISCONNECTED -> "連線已中斷Disconnected"
                RESULT_CONNECT_FAILED -> "連線失敗Connection failed"
                RESULT_CANCELED -> "已中斷連線Connection interrupted"
                else -> result.resultCode.toString()
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editIp = findViewById(R.id.editIp)
        editPort = findViewById(R.id.editPort)
        editSliderRequire1 = findViewById(R.id.sliderRequire1)
        editSliderRequire2 = findViewById(R.id.sliderRequire2)
        editEnergyDecayRate1 = findViewById(R.id.energyDecayRate1)
        editEnergyDecayRate2 = findViewById(R.id.energyDecayRate2)
        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        btnSave = findViewById(R.id.btnSave)
        txtStatus = findViewById(R.id.txtStatus)
        txtOtherMsg = findViewById(R.id.txtOtherMsg)
        sliderHeightRatio = findViewById(R.id.seekBar)
        backgroundView = findViewById(R.id.backgroundView)
        txtSliderHeight = findViewById(R.id.txtSeekValue)

        displayManager = getSystemService(DisplayManager::class.java)

        btnScan.setOnClickListener {
            if (!scanning) startScan()
        }

        btnConnect.setOnClickListener {
            val ip = editIp.text.toString()
            val port = editPort.text.toString().toIntOrNull()
            val sliderRequire1 = editSliderRequire1.text.toString().toFloatOrNull()
            val sliderRequire2 = editSliderRequire2.text.toString().toFloatOrNull()
            val energyDecayRate1 = editEnergyDecayRate1.text.toString().toFloatOrNull()
            val energyDecayRate2 = editEnergyDecayRate2.text.toString().toFloatOrNull()
            if (!Patterns.IP_ADDRESS.matcher(ip).matches()) {
                showMessage("請輸入有效的 IPv4 位址\nInvalid IPv4")
                return@setOnClickListener
            }
            if (port == null || port !in 1..65535) {
                showMessage("請輸入有效的連接埠號\nInvalid Port")
                return@setOnClickListener
            }
            if (sliderRequire1 == null || sliderRequire2 == null || sliderRequire1 <= 0f || sliderRequire2 <= 0f) {
                showMessage("Invalid slider trigger require")
                return@setOnClickListener
            }
            if (energyDecayRate1 == null || energyDecayRate2 == null) {
                showMessage("Invalid slide energy decay rate")
                return@setOnClickListener
            }
            val intent = Intent(this@MainActivity, TouchActivity::class.java)
            intent.putExtra(
                "DivaArgs",
                DivaArgs(
                    ip,
                    port,
                    sliderHeightRatio.progress,
                    sliderRequire1,
                    sliderRequire2,
                    energyDecayRate1,
                    energyDecayRate2
                )
            )
            touchActivityLauncher.launch(intent)
        }

        sliderHeightRatio.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                txtSliderHeight.text = "Slider height: ${sliderHeightRatio.progress}%"
                backgroundView.coverRatio = progress / 100f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        editSliderRequire1.setOnFocusChangeListener { _, hasFocus ->
            backgroundView.drawLine1 = hasFocus
        }
        editSliderRequire2.setOnFocusChangeListener { _, hasFocus ->
            backgroundView.drawLine2 = hasFocus
        }
        editEnergyDecayRate1.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) backgroundView.showMove1()
        }
        editEnergyDecayRate2.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) backgroundView.showMove2()
        }
        editSliderRequire1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                backgroundView.lineSpacing1 = s.toString().toFloatOrNull()
            }
        })
        editSliderRequire2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                backgroundView.lineSpacing2 = s.toString().toFloatOrNull()
            }
        })
        editEnergyDecayRate1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                backgroundView.vx1 = s.toString().toFloatOrNull()
            }
        })
        editEnergyDecayRate2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                backgroundView.vx2 = s.toString().toFloatOrNull()
            }
        })
        val prefs = getSharedPreferences("DivaPrefs", MODE_PRIVATE)
        sliderHeightRatio.progress = prefs.getInt("A", 25)
        editSliderRequire1.setText(prefs.getString("B", "39.0"))
        editSliderRequire2.setText(prefs.getString("C", "78.0"))
        editEnergyDecayRate1.setText(prefs.getString("D", "3.9"))
        editEnergyDecayRate2.setText(prefs.getString("E", "3.9"))
        btnSave.setOnClickListener {
            prefs.edit {
                putInt("A", sliderHeightRatio.progress)
                    .putString("B", editSliderRequire1.text.toString())
                    .putString("C", editSliderRequire2.text.toString())
                    .putString("D", editEnergyDecayRate1.text.toString())
                    .putString("E", editEnergyDecayRate2.text.toString())
            }
            backgroundView.showMove1()
            backgroundView.showMove2()
        }

        startScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onStart() {
        super.onStart()
        displayManager.registerDisplayListener(displayListener, null)
    }

    override fun onStop() {
        super.onStop()
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onResume() {
        super.onResume()
        checkRefreshRate()
    }

    private fun startScan() {
        scanning = true
        btnScan.isEnabled = false
        btnScan.text = "Searching..."
        val usbIpRange = findUsbTetherIpRange()
        txtStatus.text =
            if (usbIpRange == null) {
                "Broadcast search server..."
            } else {
                "Scanning ${usbIpRange.ifaceName} ${usbIpRange.cidr}..."
            }


        scope.launch {
            val found = withContext(Dispatchers.IO) {
                scanForServer(usbIpRange)
            }
            withContext(Dispatchers.Main) {
                btnScan.text = "自動搜尋伺服器Automatic search server"

                if (found != null) {
                    val (ip, port) = found
                    editIp.setText(ip)
                    editPort.setText(port.toString())
                    txtStatus.text = "Server found: $ip:$port"
                } else {
                    txtStatus.text = "Server not found."
                }
                scanning = false
                btnScan.isEnabled = true
            }
        }
    }

    private suspend fun scanForServer(range: IpRangeResult?): Pair<String, Int>? {
        try {
            DatagramSocket().use { socket ->
                val data = broadcastMessage.toByteArray()
                if (range != null) {
                    // 單播掃描
                    val start = ipv4ToInt(range.firstHost)
                    val end = ipv4ToInt(range.lastHost)
                    if (end - start > 1024) {
                        withContext(Dispatchers.Main) {
                            showMessage("Due to the large range of possible IP addresses (${range.firstHost}-${range.lastHost}), scanning the server's IP addresses has been abandoned.")
                        }
                        delay(2000.milliseconds)
                        return null
                    }
                    for (ip in start..end) {
                        socket.send(
                            DatagramPacket(
                                data,
                                data.size,
                                InetAddress.getByName(intToIpv4(ip)),
                                udpPort
                            )
                        )
                    }
                    socket.soTimeout = 139
                } else {
                    // 廣播
                    socket.broadcast = true
                    socket.send(
                        DatagramPacket(
                            data, data.size,
                            InetAddress.getByName("255.255.255.255"), udpPort
                        )
                    )
                    socket.soTimeout = 831
                }

                val buf = ByteArray(256)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)

                val msg = String(response.data, 0, response.length).trim()
                if (msg.startsWith("Miku here: ")) {
                    val portStr = msg.substringAfter("Miku here: ", "")
                    val port = portStr.toIntOrNull() ?: return null
                    val ip = response.address.hostAddress ?: return null
                    return Pair(ip, port)
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        txtStatus.text = msg
    }

    private fun checkRefreshRate() {
        val display = windowManager.defaultDisplay
        val modes = display.supportedModes
        var maxRefreshRate = 0.0f
        for (mode in modes) {
            maxRefreshRate = max(maxRefreshRate, mode.refreshRate)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val otherRates = mode.alternativeRefreshRates
                if (otherRates.isNotEmpty()) {
                    for (alternativeRate in otherRates) {
                        maxRefreshRate = max(maxRefreshRate, alternativeRate)
                    }
                } else {
                    // 在我的 Galaxy Tap S7+ (API level 33)上，不知為何mode.alternativeRefreshRates是空的
                    // 但是mode.toString()又確實顯示有alternativeRefreshRates
                    // 不確定是不是系統實作上有問題， 總之加個備用workaround吧
                    val modeStr = mode.toString()
                    val prefix = "alternativeRefreshRates=["
                    val start = modeStr.indexOf(prefix)
                    if ((start != -1)) {
                        val end = modeStr.indexOf(']', start)
                        if (end != -1) {
                            for (s in modeStr.substring(start + prefix.length, end).split(',')) {
                                val f = s.toFloatOrNull()
                                if (f != null) {
                                    maxRefreshRate = max(maxRefreshRate, f)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (display.refreshRate < maxRefreshRate) {
            txtOtherMsg.text =
                "The current refresh rate is ${display.refreshRate}Hz. Your device supports ${maxRefreshRate}Hz.\nA higher refresh rate results in lower latency. (Even if the hardware supports a higher touch sampling rate, the actual touch events received by the application will still be limited by the screen refresh rate.)"
        } else {
            txtOtherMsg.text = "The current refresh rate is ${display.refreshRate}Hz."
        }
    }

}

data class IpRangeResult(
    val ifaceName: String,
    val addr: String,       // the interface IP, e.g. "10.211.32.1"
    val prefixLen: Int,     // e.g. 24
    val cidr: String,       // e.g. "10.211.32.0/24"
    val firstHost: String,  // e.g. "10.211.32.1" (or .1 depending on network)
    val lastHost: String    // e.g. "10.211.32.254"
)

/**
 * 返回 USB (或 RNDIS-like) 介面的 IPv4 範圍，如果沒有找到則回 null。
 */
fun findUsbTetherIpRange(): IpRangeResult? {

    fun makeResult(name: String, addr: Inet4Address, prefix: Int): IpRangeResult {
        val ip = addr.hostAddress!!
        val netAddr = ipv4ToInt(ip) and prefixToMask(prefix)
        val hostCount = 1 shl (32 - prefix)
        val first = if (hostCount > 2) netAddr + 1 else netAddr
        val last = if (hostCount > 2) netAddr + hostCount - 2 else netAddr + hostCount - 1
        return IpRangeResult(
            name,
            ip,
            prefix,
            "${intToIpv4(netAddr)}/$prefix",
            intToIpv4(first),
            intToIpv4(last)
        )
    }

    // --------------- 1) 直接檢查系統介面 usb/rndis/ncm/eth -------------------
    NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
        val name = ni.name.lowercase()
        if (!name.matches(Regex("(usb\\d*|rndis\\d*|ncm\\d*|eth\\d*)"))) return@forEach  // ← 只接受 USB/RNDIS 類

        ni.interfaceAddresses.forEach { ia ->
            val addr = ia.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                return makeResult(ni.name, addr, ia.networkPrefixLength.toInt())
            }
        }
    }
    // --------------- 2) 沒第二步了，直接當作沒有 -------------------
    return null
}

private fun ipv4ToInt(ip: String): Int {
    val parts = ip.split(".").map { it.toInt() and 0xFF }
    return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
}

private fun intToIpv4(value: Int): String {
    val a = (value shr 24) and 0xFF
    val b = (value shr 16) and 0xFF
    val c = (value shr 8) and 0xFF
    val d = (value) and 0xFF
    return "$a.$b.$c.$d"
}

private fun prefixToMask(prefixLen: Int): Int {
    return if (prefixLen == 0) 0 else (0xFFFFFFFF.toInt() shl (32 - prefixLen))
}