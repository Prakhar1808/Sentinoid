package com.sentinoid.shield

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

/**
 * Power-optimized USB Bridge with buffer pooling and coroutine-based async I/O.
 * Reduces CPU wakeups and memory allocations during AOA communication.
 */
class UsbBridgeManager(private val context: Context) {
    private val TAG = "UsbBridgeManager"
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var accessory: UsbAccessory? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Buffer pool to reduce GC pressure
    private val bufferPool = ArrayBlockingQueue<ByteBuffer>(5)
    private val BUFFER_SIZE = 8192 // 8KB for power efficiency
    
    interface AoaListener {
        fun onConnected(accessory: UsbAccessory)
        fun onDisconnected()
        fun onDataReceived(data: ByteArray)
    }
    
    private var listener: AoaListener? = null
    
    init {
        // Pre-allocate buffers
        repeat(5) {
            bufferPool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE))
        }
    }
    
    fun setListener(listener: AoaListener) {
        this.listener = listener
    }
    
    fun connectToAccessory(accessory: UsbAccessory): Boolean {
        return try {
            parcelFileDescriptor = usbManager.openAccessory(accessory)
            val fd = parcelFileDescriptor?.fileDescriptor
            inputStream = FileInputStream(fd)
            outputStream = FileOutputStream(fd)
            this.accessory = accessory
            isRunning.set(true)
            startListening()
            listener?.onConnected(accessory)
            Log.i(TAG, "Connected to AOA accessory: ${accessory.model}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to accessory", e)
            false
        }
    }
    
    /**
     * Async send with backpressure handling for power efficiency.
     */
    suspend fun sendDataAsync(data: ByteArray): Boolean {
        return try {
            // Batch small writes for efficiency
            with(java.nio.channels.Channels.newChannel(outputStream!!)) {
                write(ByteBuffer.wrap(data))
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }
    
    fun sendData(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }
    
    /**
     * Flow-based data reception with backpressure handling.
     */
    fun receiveDataFlow(): Flow<ByteArray> = flow {
        val job = currentCoroutineContext()[Job]
        while (job?.isActive == true && isRunning.get()) {
            val buffer = bufferPool.poll() ?: ByteBuffer.allocateDirect(BUFFER_SIZE)
            
            try {
                val bytesRead = inputStream?.read(buffer.array()) ?: -1
                if (bytesRead > 0) {
                    val data = buffer.array().copyOf(bytesRead)
                    emit(data)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error reading from accessory", e)
                break
            } finally {
                buffer.clear()
                bufferPool.offer(buffer)
            }
            
            // Adaptive delay based on power mode
            if (isPowerSaveMode()) {
                delay(10)
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private fun startListening() {
        scope.launch {
            try {
                val buffer = bufferPool.poll() ?: ByteBuffer.allocateDirect(BUFFER_SIZE)
                
                while (isActive && isRunning.get()) {
                    val bytesRead = try {
                        inputStream?.read(buffer.array()) ?: -1
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading", e)
                        break
                    }
                    
                    if (bytesRead > 0) {
                        val data = buffer.array().copyOf(bytesRead)
                        listener?.onDataReceived(data)
                    }
                    
                    buffer.clear()
                    
                    // Yield in power save mode
                    if (isPowerSaveMode()) {
                        kotlinx.coroutines.delay(5)
                    }
                }
                
                bufferPool.offer(buffer)
            } catch (e: Exception) {
                Log.e(TAG, "Listener error", e)
            }
        }
    }
    
    fun disconnect() {
        isRunning.set(false)
        scope.cancel()
        
        try {
            inputStream?.close()
            outputStream?.close()
            parcelFileDescriptor?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing streams", e)
        }
        
        // Clear buffer pool
        bufferPool.clear()
        
        listener?.onDisconnected()
        Log.i(TAG, "Disconnected from AOA accessory")
    }
    
    fun isConnected(): Boolean = isRunning.get() && accessory != null
    
    private fun isPowerSaveMode(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            powerManager.isPowerSaveMode
        } else {
            false
        }
    }
}
