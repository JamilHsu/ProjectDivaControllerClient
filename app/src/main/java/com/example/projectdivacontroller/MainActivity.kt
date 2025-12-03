package com.example.projectdivacontroller
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import java.net.*


const val RESULT_CONNECT_FAILED = 10000
const val RESULT_DISCONNECTED = 10001

@SuppressLint("SetTextI18n")
class MainActivity : ComponentActivity() {

    private val udpPort = 39831
    private val broadcastMessage = "Miku Miku where are you?" //其實這個訊息是什麼都可以，目前根本沒檢查訊息的內容

    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var btnScan: Button
    private lateinit var btnConnect: Button
    private lateinit var txtStatus: TextView

    private var scanning = false
    private var connecting = false

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
        btnScan = findViewById(R.id.btnScan)
        btnConnect = findViewById(R.id.btnConnect)
        txtStatus = findViewById(R.id.txtStatus)

        btnScan.setOnClickListener {
            if (!scanning && !connecting) startScan()
        }

        btnConnect.setOnClickListener {
            if (!connecting) {
                val ip = editIp.text.toString().trim()
                val port = editPort.text.toString().trim().toIntOrNull()
                if (!Patterns.IP_ADDRESS.matcher(ip).matches()) {
                    showMessage("請輸入有效的 IPv4 位址\nInvalid IPv4")
                    return@setOnClickListener
                }
                if (port == null || port !in 1..65535) {
                    showMessage("請輸入有效的連接埠號\nInvalid Port")
                    return@setOnClickListener
                }

                connectToServer(ip, port)
            }
        }
    }

    private fun startScan() {
        scanning = true
        btnScan.isEnabled = false
        btnScan.text = "Searching..."
        val usbIpRange = findUsbTetherIpRange()
        txtStatus.text =
            if (usbIpRange==null)
                {"Broadcast search server..."}
            else
                {"Scanning ${usbIpRange.cidr}..."}


        scope.launch {
            val found = withContext(Dispatchers.IO) {
                scanForServer(usbIpRange)
            }
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

    private fun scanForServer(range: IpRangeResult?): Pair<String, Int>? {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1000

                val data = broadcastMessage.toByteArray()
                if (range != null) {
                    // 單播掃描
                    val start = ipv4ToInt(range.firstHost)
                    val end = ipv4ToInt(range.lastHost)
                    for (ip in start..end){
                        socket.send(DatagramPacket(
                            data,
                            data.size,
                            InetAddress.getByName(intToIpv4(ip)),
                            udpPort))
                    }
                } else {
                    // 廣播
                    socket.broadcast = true
                    socket.send(DatagramPacket(data, data.size,
                        InetAddress.getByName("255.255.255.255"), udpPort
                    ))
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
        } catch (_: Exception) {}
        return null
    }

    private fun connectToServer(ip: String, port: Int) {
        connecting = true
        btnConnect.isEnabled = false

        scope.launch {
            // 跳轉 TouchActivity 並等待回傳結果
            val intent = Intent(this@MainActivity, TouchActivity::class.java)
            intent.putExtra("ip", ip)
            intent.putExtra("port", port)
            touchActivityLauncher.launch(intent)

            connecting = false
            btnConnect.isEnabled = true
        }
    }

    private fun showMessage(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        txtStatus.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
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
        val last  = if (hostCount > 2) netAddr + hostCount - 2 else netAddr + hostCount - 1
        return IpRangeResult(name, ip, prefix, "${intToIpv4(netAddr)}/$prefix", intToIpv4(first), intToIpv4(last))
    }

    // --------------- 1) 直接檢查系統介面 usb/rndis/eth -------------------
    NetworkInterface.getNetworkInterfaces().toList().forEach { ni ->
        val name = ni.name.lowercase()
        if (!name.matches(Regex("(usb\\d*|rndis\\d*|eth\\d*)"))) return@forEach  // ← 只接受 USB/RNDIS 類

        ni.interfaceAddresses.forEach { ia ->
            val addr = ia.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                return makeResult(name, addr, ia.networkPrefixLength.toInt())
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