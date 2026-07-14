package com.example.projectdivacontroller

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TcpClient(
    private val host: String,
    private val port: Int,
    private val onDisconnect: (() -> Unit)? = null
) {
    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var input: InputStream? = null
    private var recvJob: Job? = null
    private val writeLock = Any()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket!!.connect(InetSocketAddress(host, port), 1500)

            socket!!.tcpNoDelay = true

            output = socket!!.getOutputStream()
            input = socket!!.getInputStream()
            // 傳送不使用協程而是直接發送

            // 啟動接收協程
            recvJob = scope.launch {
                try {
                    val reader = input ?: return@launch
                    val inputBuffer = ByteArray(512)
                    val outputBuffer = ByteBuffer.allocate(1088).order(ByteOrder.LITTLE_ENDIAN)
                    while (isActive) {
                        val len = reader.read(inputBuffer, 0, 512)
                        if (len == -1) break // 連線中斷
                        if (len % 8 != 0) continue
                        val timestamp = System.nanoTime()
                        var offset = 0
                        while (offset != len) {
                            outputBuffer.put('R'.code.toByte())
                            outputBuffer.put(inputBuffer, offset, 8)
                            outputBuffer.putLong(timestamp)
                            offset += 8
                        }
                        send(outputBuffer)
                        outputBuffer.clear()
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

    fun send(msg: ByteBuffer) {
        synchronized(writeLock) {
            try {
                val out = output ?: return
                out.write(msg.array(), 0, msg.position())
            } catch (_: Exception) {
                onDisconnect?.invoke()
            }
        }
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        scope.cancel()
    }
}

