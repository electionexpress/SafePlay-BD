package bd.safeplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SafePlayVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val active = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!active.get()) establishDnsVpn()
        return START_STICKY
    }

    private fun establishDnsVpn() {
        try {
            vpnInterface = Builder()
                .setSession("SafePlay BD DNS Protection")
                .setMtu(1500)
                .addAddress("10.77.0.1", 32)
                .addDnsServer("10.77.0.2")
                .addRoute("10.77.0.2", 32)
                .establish()

            if (vpnInterface == null) {
                stopSelf()
                return
            }
            active.set(true)
            isRunning = true
            thread(name = "SafePlay-DNS") { packetLoop() }
        } catch (_: Exception) {
            isRunning = false
            stopSelf()
        }
    }

    private fun packetLoop() {
        val descriptor = vpnInterface ?: return
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        while (active.get()) {
            try {
                val length = input.read(buffer)
                if (length > 0) {
                    processDnsPacket(buffer.copyOf(length))?.let(output::write)
                }
            } catch (_: Exception) {
                if (!active.get()) break
            }
        }
        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    private fun processDnsPacket(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() ushr 4) and 0x0f
        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        if (version != 4 || packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xff) != 17) return null
        val udpOffset = ipHeaderLength
        val clientPort = readU16(packet, udpOffset)
        val serverPort = readU16(packet, udpOffset + 2)
        if (serverPort != 53) return null
        val dnsOffset = udpOffset + 8
        val dnsLength = readU16(packet, udpOffset + 4) - 8
        if (dnsLength < 12 || dnsOffset + dnsLength > packet.size) return null
        val query = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val domain = readQuestionName(query) ?: return null

        val answer = if (Blocklist.isBlocked(domain)) {
            query.copyOf().apply {
                this[2] = 0x81.toByte()
                this[3] = 0x83.toByte()
                this[6] = 0
                this[7] = 0
                this[8] = 0
                this[9] = 0
                this[10] = 0
                this[11] = 0
            }
        } else {
            forwardDns(query) ?: return null
        }
        return makeResponsePacket(packet, clientPort, answer)
    }

    private fun readQuestionName(dns: ByteArray): String? {
        if (dns.size < 17) return null
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < dns.size) {
            val length = dns[offset].toInt() and 0xff
            offset++
            if (length == 0) break
            if (length > 63 || offset + length > dns.size) return null
            labels += String(dns, offset, length, Charsets.US_ASCII)
            offset += length
        }
        if (labels.isEmpty() || offset + 4 > dns.size) return null
        return labels.joinToString(".").lowercase().trimEnd('.')
    }

    private fun forwardDns(query: ByteArray): ByteArray? = try {
        DatagramSocket().use { socket ->
            protect(socket)
            socket.soTimeout = 3000
            val address = InetAddress.getByName("1.1.1.1")
            socket.send(DatagramPacket(query, query.size, address, 53))
            val bytes = ByteArray(4096)
            val reply = DatagramPacket(bytes, bytes.size)
            socket.receive(reply)
            bytes.copyOf(reply.length)
        }
    } catch (_: Exception) {
        null
    }

    private fun makeResponsePacket(original: ByteArray, clientPort: Int, dns: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dns.size
        val result = ByteArray(totalLength)
        result[0] = 0x45
        putU16(result, 2, totalLength)
        putU16(result, 6, 0x4000)
        result[8] = 64
        result[9] = 17
        System.arraycopy(original, 16, result, 12, 4)
        System.arraycopy(original, 12, result, 16, 4)
        putU16(result, 10, checksum(result, 0, 20))
        putU16(result, 20, 53)
        putU16(result, 22, clientPort)
        putU16(result, 24, 8 + dns.size)
        System.arraycopy(dns, 0, result, 28, dns.size)
        return result
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var index = offset
        while (index < offset + length - 1) {
            sum += (((data[index].toInt() and 0xff) shl 8) or
                (data[index + 1].toInt() and 0xff)).toLong()
            index += 2
        }
        if (length % 2 != 0) {
            sum += ((data[offset + length - 1].toInt() and 0xff) shl 8).toLong()
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xffff) + (sum ushr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    override fun onDestroy() {
        active.set(false)
        isRunning = false
        try { vpnInterface?.close() } catch (_: Exception) {}
        vpnInterface = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SafePlay Protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setContentTitle("SafePlay BD")
            .setContentText("DNS সুরক্ষা চালু আছে")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "safeplay_protection"
        private const val NOTIFICATION_ID = 7701
    }
}
