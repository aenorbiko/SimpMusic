package com.maxrave.simpmusic.ui.component

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "lyrics_output_offsets_v1"

private data class BluetoothOutput(val key: String, val name: String)

/**
 * Android 13+: use the actual MEDIA route, not merely a connected Bluetooth
 * headset. This avoids applying the car profile when music plays on the phone.
 * Older Android falls back only when exactly one Bluetooth output is attached.
 */
private fun activeBluetoothOutput(manager: AudioManager): BluetoothOutput? {
    val candidates = try {
        if (Build.VERSION.SDK_INT >= 33) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            manager.getAudioDevicesForAttributes(attributes)
        } else {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    val bluetooth = candidates.filter { info ->
        when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
            else -> false
        }
    }
    if (bluetooth.size != 1) return null
    val info = bluetooth.first()
    // AudioDeviceInfo.id is not stable across reconnects. Prefer route address
    // and fall back to the product label; nothing is sent to a network service.
    val name = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth audio"
    val address = if (Build.VERSION.SDK_INT >= 28) info.address.orEmpty() else ""
    return BluetoothOutput(
        key = "bt:" + (address.ifBlank { name }),
        name = name,
    )
}

@Composable
actual fun rememberLyricsOutputOffsetProfile(
    globalOffsetMs: Int,
    updateGlobalOffset: (Int) -> Unit,
): LyricsOutputOffsetProfile {
    val context = LocalContext.current.applicationContext
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val preferences = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var output by remember(audioManager) { mutableStateOf(activeBluetoothOutput(audioManager)) }

    // Callback is removed with the composable; it neither scans Bluetooth nor
    // asks for Bluetooth permissions. Re-evaluate on route plug/unplug.
    DisposableEffect(audioManager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                output = activeBluetoothOutput(audioManager)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                output = activeBluetoothOutput(audioManager)
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        output = activeBluetoothOutput(audioManager)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    val key = output?.key
    var activeOffset by remember(key, globalOffsetMs) {
        mutableIntStateOf(
            if (key != null && preferences.contains(key)) {
                preferences.getInt(key, globalOffsetMs)
            } else globalOffsetMs,
        )
    }
    return LyricsOutputOffsetProfile(
        offsetMs = activeOffset,
        deviceName = output?.name,
        updateOffset = { adjusted ->
            activeOffset = adjusted
            if (key == null) updateGlobalOffset(adjusted)
            else preferences.edit().putInt(key, adjusted).apply()
        },
    )
}
