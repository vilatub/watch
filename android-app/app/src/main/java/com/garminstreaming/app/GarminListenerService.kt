package com.garminstreaming.app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice

/**
 * Service that listens for messages from the Garmin watch app
 *
 * Note: This requires the Garmin Connect IQ Mobile SDK.
 * Download from: https://developer.garmin.com/connect-iq/sdk/
 */
class GarminListenerService : Service() {

    companion object {
        private const val TAG = "GarminListenerService"

        // Must match the app ID in watch-app/manifest.xml
        const val WATCH_APP_ID = "a3421fe8-d665-4df2-b2d3-123456789abc"
    }

    private var connectIQ: ConnectIQ? = null
    private var connectedDevice: IQDevice? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        initializeConnectIQ()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownConnectIQ()
        Log.d(TAG, "Service destroyed")
    }

    private fun initializeConnectIQ() {
        try {
            connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)

            connectIQ?.initialize(this, true, object : ConnectIQ.ConnectIQListener {
                override fun onSdkReady() {
                    Log.d(TAG, "Connect IQ SDK ready")
                    ActivityRepository.setConnectionStatus(ConnectionStatus.CONNECTING)
                    registerForDeviceEvents()
                }

                override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus?) {
                    Log.e(TAG, "Connect IQ initialization error: $status")
                    ActivityRepository.setConnectionStatus(ConnectionStatus.DISCONNECTED)
                }

                override fun onSdkShutDown() {
                    Log.d(TAG, "Connect IQ SDK shut down")
                    ActivityRepository.setConnectionStatus(ConnectionStatus.DISCONNECTED)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Connect IQ", e)
        }
    }

    private fun registerForDeviceEvents() {
        try {
            val devices = connectIQ?.knownDevices ?: return

            if (devices.isEmpty()) {
                Log.d(TAG, "No known devices")
                return
            }

            for (device in devices) {
                connectIQ?.registerForDeviceEvents(device) { device, status ->
                    when (status) {
                        IQDevice.IQDeviceStatus.CONNECTED -> {
                            Log.d(TAG, "Device connected: ${device.friendlyName}")
                            connectedDevice = device
                            ActivityRepository.setConnectionStatus(ConnectionStatus.CONNECTED)
                            registerForAppMessages(device)
                        }
                        IQDevice.IQDeviceStatus.NOT_CONNECTED -> {
                            Log.d(TAG, "Device disconnected: ${device.friendlyName}")
                            if (connectedDevice == device) {
                                connectedDevice = null
                                ActivityRepository.setConnectionStatus(ConnectionStatus.DISCONNECTED)
                            }
                        }
                        else -> {
                            Log.d(TAG, "Device status: $status")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register for device events", e)
        }
    }

    private fun registerForAppMessages(device: IQDevice) {
        try {
            val app = IQApp(WATCH_APP_ID)

            connectIQ?.registerForAppEvents(device, app) { device, app, message, status ->
                Log.d(TAG, "Received message from ${device.friendlyName}: $message")

                if (status == ConnectIQ.IQMessageStatus.SUCCESS && message != null) {
                    processMessage(message)
                }
            }

            Log.d(TAG, "Registered for app messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register for app messages", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processMessage(message: Any) {
        try {
            val messageList = message as? List<Any> ?: return
            val dataMap = messageList.firstOrNull() as? Map<String, Any> ?: return

            val type = dataMap["type"] as? String
            if (type != "activity_data") return

            val activityTypeStr = (dataMap["activity_type"] as? String) ?: "running"

            val activityData = ActivityData(
                timestamp = (dataMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                heartRate = (dataMap["hr"] as? Number)?.toInt() ?: 0,
                latitude = (dataMap["lat"] as? Number)?.toDouble() ?: 0.0,
                longitude = (dataMap["lon"] as? Number)?.toDouble() ?: 0.0,
                speed = (dataMap["speed"] as? Number)?.toDouble() ?: 0.0,
                altitude = (dataMap["altitude"] as? Number)?.toDouble() ?: 0.0,
                distance = (dataMap["distance"] as? Number)?.toDouble() ?: 0.0,
                cadence = (dataMap["cadence"] as? Number)?.toInt() ?: 0,
                power = (dataMap["power"] as? Number)?.toInt() ?: 0,
                activityType = ActivityType.fromString(activityTypeStr)
            )

            ActivityRepository.updateData(activityData)
            ActivityRepository.setConnectionStatus(ConnectionStatus.STREAMING)

            Log.d(TAG, "Activity data: HR=${activityData.heartRate}, " +
                    "Type=${activityData.activityType}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process message", e)
        }
    }

    private fun shutdownConnectIQ() {
        try {
            connectIQ?.shutdown(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown Connect IQ", e)
        }
    }
}
