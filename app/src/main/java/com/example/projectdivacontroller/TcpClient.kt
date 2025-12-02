package com.example.projectdivacontroller

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue

class TcpClient(
    private val host: String,
    private val port: Int,
    private val onDisconnect: (() -> Unit)? = null
) {
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var input: BufferedReader? = null
    private var sendJob: Job? = null
    private var recvJob: Job? = null
    private val sendQueue = LinkedBlockingQueue<String>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket!!.connect(InetSocketAddress(host, port), 1500)

            socket!!.tcpNoDelay = true

            output = socket!!.getOutputStream()
            input = BufferedReader(InputStreamReader(socket!!.getInputStream(), Charsets.UTF_8))
            // 啟動傳送協程
            sendJob = scope.launch {
                try {
                    val out = output ?: return@launch
                    while (isActive) {
                        // 等待至少一條訊息
                        var msg = sendQueue.take()
                        // 傳送直到沒有訊息
                        do {
                            val data = msg.toByteArray(Charsets.UTF_8)
                            out.write(data)
                            msg = sendQueue.poll()
                        } while (msg != null)
                    }
                } catch (_: Exception) {
                    onDisconnect?.invoke()
                }
            }
            // 啟動接收協程
            recvJob = scope.launch {
                try {
                    val reader = input ?: return@launch
                    val buffer = CharArray(1024)
                    while (isActive) {
                        val len = reader.read(buffer, 0, 1023)
                        if (len == -1) break // 連線中斷
                        buffer[len] = 0.toChar()
                        var end = 0
                        do {
                            val start = end
                            while (end < len && buffer[end] != '\n') {
                                ++end
                            }
                            ++end
                            when (buffer[start]) {
                                'T' -> sendDelayTest()
                                'P' -> {
                                    buffer[start + 1] = 'O'
                                    send(String(buffer, start, end))
                                }

                                else -> {}
                            }
                        } while (end < len)
                    }
                } catch (_: Exception) {
                    onDisconnect?.invoke()
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun send(msg: String) {
        sendQueue.offer(msg)
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) { }
        sendJob?.cancel()
        scope.cancel()
    }
    //開始傳送
    suspend fun sendDelayTest(){
        repeat(100) {
            send("T " + System.nanoTime().toString() + "\n")
            delay(5)
        }
    }
}

