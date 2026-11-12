package io.github.byjoelsamuel.drivesanity.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** Classic Bluetooth adapters (most ELM327s): a serial port over RFCOMM. Pair them in Android's settings first. */
@SuppressLint("MissingPermission")
class ClassicTransport(device: BluetoothDevice) : ObdTransport {
    private val socket: BluetoothSocket = connect(device)
    private val input = socket.inputStream
    private val output = socket.outputStream

    private fun connect(device: BluetoothDevice): BluetoothSocket {
        val secure = device.createRfcommSocketToServiceRecord(SPP)
        try {
            secure.connect()
            return secure
        } catch (e: IOException) {
            runCatching { secure.close() }
        }
        // Some cheap adapters only accept an insecure connection.
        val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP)
        try {
            insecure.connect()
            return insecure
        } catch (e: IOException) {
            runCatching { insecure.close() }
        }
        // Last try, the old way: a fixed channel. Some phones (Samsung, OnePlus among them) and cheap adapters
        // only manage it like this when the usual lookup fails.
        val fixed = runCatching {
            device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType).invoke(device, 1) as BluetoothSocket
        }.getOrNull()
        if (fixed != null) {
            try {
                fixed.connect()
                return fixed
            } catch (e: IOException) {
                runCatching { fixed.close() }
            }
        }
        throw IOException("Couldn't connect to ${device.address}. Is the adapter plugged in and the car on?")
    }

    override fun send(text: String) {
        output.write(text.toByteArray(Charsets.US_ASCII))
        output.flush()
    }

    override fun readReply(timeoutMs: Long): String {
        val sb = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (Thread.interrupted()) throw InterruptedIOException()
            if (input.available() > 0) {
                val b = input.read()
                if (b < 0) throw IOException("Adapter disconnected")
                if (b == '>'.code) return sb.toString()
                sb.append(b.toChar())
            } else {
                if (SystemClock.elapsedRealtime() > deadline) throw IOException("Adapter stopped answering")
                Thread.sleep(5)
            }
        }
    }

    override fun drain() {
        try {
            while (input.available() > 0) input.read()
        } catch (_: IOException) {
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

/**
 * Bluetooth Low Energy adapters (Vgate iCar BLE, Veepeak BLE, OBDLink CX, the "iPhone" ones):
 * the same ELM327 text, sent through a write characteristic, with replies arriving as
 * notifications. No pairing needed.
 */
@SuppressLint("MissingPermission")
class BleTransport(context: Context, device: BluetoothDevice) : ObdTransport {
    private val incoming = StringBuilder()
    private val ready = CountDownLatch(1)
    private val descriptorWritten = CountDownLatch(1)
    private val writeDone = Semaphore(0)
    @Volatile private var disconnected = false
    private lateinit var writeChar: BluetoothGattCharacteristic

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnected = true
                ready.countDown()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = ready.countDown()

        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) =
            descriptorWritten.countDown()

        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) =
            writeDone.release()

        // Android 13+ calls this one.
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) =
            received(value)

        // Older Android calls this one.
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) c.value?.let { received(it) }
        }
    }

    private val gatt: BluetoothGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

    init {
        try {
            if (!ready.await(15, TimeUnit.SECONDS) || disconnected) {
                throw IOException("Couldn't connect to ${device.address}. Is the adapter plugged in?")
            }
            setUpCharacteristics()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun setUpCharacteristics() {
        val skip = setOf(0x1800, 0x1801, 0x180A, 0x180F) // generic access/attribute, device info, battery
        val known = listOf(0xFFF0, 0xFFE0, 0x18F0)          // common ELM327 BLE services
        val candidates = gatt.services
            .filter { shortId(it.uuid) !in skip }
            .sortedBy { known.indexOf(shortId(it.uuid)).let { i -> if (i < 0) 99 else i } }
        for (service in candidates) {
            val notify = service.characteristics.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            } ?: continue
            val write = service.characteristics.firstOrNull {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } ?: continue
            writeChar = write
            enableNotifications(notify)
            return
        }
        throw IOException("This Bluetooth device doesn't look like an OBD2 adapter")
    }

    @Suppress("DEPRECATION")
    private fun enableNotifications(c: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(CCCD) ?: return
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(cccd, value)
        } else {
            cccd.value = value
            gatt.writeDescriptor(cccd)
        }
        descriptorWritten.await(3, TimeUnit.SECONDS)
    }

    private fun received(bytes: ByteArray) {
        synchronized(incoming) { incoming.append(String(bytes, Charsets.US_ASCII)) }
    }

    @Suppress("DEPRECATION")
    override fun send(text: String) {
        if (disconnected) throw IOException("Adapter disconnected")
        val type = if (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        for (chunk in text.toByteArray(Charsets.US_ASCII).toList().chunked(20)) {
            val bytes = chunk.toByteArray()
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(writeChar, bytes, type)
            } else {
                writeChar.writeType = type
                writeChar.value = bytes
                gatt.writeCharacteristic(writeChar)
            }
            writeDone.tryAcquire(1, TimeUnit.SECONDS)
        }
    }

    override fun readReply(timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (Thread.interrupted()) throw InterruptedIOException()
            if (disconnected) throw IOException("Adapter disconnected")
            synchronized(incoming) {
                val end = incoming.indexOf(">")
                if (end >= 0) {
                    val reply = incoming.substring(0, end)
                    incoming.delete(0, end + 1)
                    return reply
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) throw IOException("Adapter stopped answering")
            Thread.sleep(5)
        }
    }

    override fun drain() {
        synchronized(incoming) { incoming.setLength(0) }
    }

    override fun close() {
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    companion object {
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private fun shortId(uuid: UUID): Int {
            val s = uuid.toString()
            return if (s.endsWith("-0000-1000-8000-00805f9b34fb")) s.substring(4, 8).toInt(16) else -1
        }
    }
}
