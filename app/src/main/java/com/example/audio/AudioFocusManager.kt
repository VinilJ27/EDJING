package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioRouteState(
    val outputDeviceLabel: String = "Speaker",
    val isBluetoothConnected: Boolean = false,
    val isWiredConnected: Boolean = false,
    val hasAudioFocus: Boolean = false
)

class AudioFocusManager(
    private val context: Context,
    private val onPauseRequested: () -> Unit,
    private val onDuckChanged: (Boolean) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routeState = MutableStateFlow(AudioRouteState())
    val routeState: StateFlow<AudioRouteState> = _routeState.asStateFlow()

    private var focusRequest: AudioFocusRequest? = null
    private var receiverRegistered = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                _routeState.value = _routeState.value.copy(hasAudioFocus = false)
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                _routeState.value = _routeState.value.copy(hasAudioFocus = false)
                onPauseRequested()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onDuckChanged(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                _routeState.value = _routeState.value.copy(hasAudioFocus = true)
                onDuckChanged(false)
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPauseRequested()
                refreshOutputDevices()
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices()
        }
    }

    fun register() {
        if (!receiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            receiverRegistered = true
        }
        refreshOutputDevices()
    }

    fun unregister() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(noisyReceiver) } catch (_: Exception) {}
            try { audioManager.unregisterAudioDeviceCallback(deviceCallback) } catch (_: Exception) {}
            receiverRegistered = false
        }
        abandonFocus()
    }

    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        _routeState.value = _routeState.value.copy(hasAudioFocus = granted)
        return granted
    }

    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
        _routeState.value = _routeState.value.copy(hasAudioFocus = false)
    }

    fun refreshOutputDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var bt = false
        var wired = false
        var label = "Built-in Speaker"

        for (dev in outputs) {
            when (dev.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                    bt = true
                    label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio"
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    wired = true
                    label = dev.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired / USB Audio"
                }
            }
        }
        _routeState.value = _routeState.value.copy(
            outputDeviceLabel = label,
            isBluetoothConnected = bt,
            isWiredConnected = wired
        )
    }
}
