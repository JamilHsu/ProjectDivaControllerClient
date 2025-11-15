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
class MainActivity : ComponentActivity() {

    private val udpPort = 39831
    private val broadcastMessage = "DISCOVER_SERVER"

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
        when (result.resultCode) {
            RESULT_DISCONNECTED -> txtStatus.text = "連線已中斷"
            RESULT_CONNECT_FAILED -> txtStatus.text = "連線失敗"
            RESULT_CANCELED -> "已中斷連線"
            else -> txtStatus.text = result.resultCode.toString()
        }
    }

    @SuppressLint("MissingInflatedId")
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
                    showMessage("請輸入有效的 IPv4 位址")
                    return@setOnClickListener
                }
                if (port == null || port !in 1..65535) {
                    showMessage("請輸入有效的連接埠號 (1–65535)")
                    return@setOnClickListener
                }

                connectToServer(ip, port)
            }
        }
    }

    private fun startScan() {
        scanning = true
        btnScan.isEnabled = false
        btnScan.text = "自動搜尋中..."
        txtStatus.text = "正在廣播尋找伺服器..."

        scope.launch {
            val found = withContext(Dispatchers.IO) { scanForServer() }
            scanning = false
            btnScan.isEnabled = true
            btnScan.text = "自動搜尋伺服器"

            if (found != null) {
                val (ip, port) = found
                editIp.setText(ip)
                editPort.setText(port.toString())
                txtStatus.text = "找到伺服器：$ip:$port"
            } else {
                txtStatus.text = "未找到伺服器"
            }
        }
    }

    private fun scanForServer(): Pair<String, Int>? {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1000

                val data = broadcastMessage.toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), udpPort)
                socket.send(packet)

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
            connecting = false
            btnConnect.isEnabled = true

            // 跳轉 TouchActivity 並等待回傳結果
            val intent = Intent(this@MainActivity, TouchActivity::class.java)
            intent.putExtra("ip", ip)
            intent.putExtra("port", port)
            touchActivityLauncher.launch(intent)
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
