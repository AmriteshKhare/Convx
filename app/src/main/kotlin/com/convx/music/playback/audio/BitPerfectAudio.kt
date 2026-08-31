/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.media.AudioAttributes as PlatformAudioAttributes

/**
 * True bit-perfect PCM output — decoded samples reaching the DAC with no
 * resample, no mix, no volume scaling — only exists on Android 14+ (API 34),
 * only to a connected USB DAC, and only where the OEM has implemented the
 * low-level mixer support (source.android.com/docs/core/audio/preferred-mixer-attr).
 * As of this writing that's a short device list (confirmed on Pixel 9+; most
 * other devices, including older Pixels, report no bit-perfect mixer at all).
 * Everywhere else this is a safe no-op and playback stays on the normal path —
 * callers should pair it with [com.convx.music.eq.EqualizerService.setBitPerfectBypass]
 * and skip-silence, which are the parts of "bit perfect" every device can honor.
 */
object BitPerfectAudio {

    private fun platformAttributes(): PlatformAudioAttributes =
        PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    @SuppressLint("NewApi")
    private fun usbOutputDevice(audioManager: AudioManager): AudioDeviceInfo? =
        audioManager.getAudioDevicesForAttributes(platformAttributes())
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }

    /** Whether this device+route can honor a real hardware bit-perfect mixer right now. */
    @SuppressLint("NewApi")
    fun isHardwareBitPerfectAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        val device = usbOutputDevice(audioManager) ?: return false
        return audioManager.getSupportedMixerAttributes(device)
            .any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
    }

    /**
     * Best-effort: ask AudioFlinger to route the media stream through the
     * bit-perfect mixer for the track currently playing at [sampleRate]/[encoding]
     * (an `android.media.AudioFormat` encoding constant — Media3's `Format.pcmEncoding`
     * uses the same integer space). Returns whether it took; false just means
     * playback continues on the normal mixed path.
     */
    @SuppressLint("NewApi")
    fun requestBitPerfectMixer(context: Context, sampleRate: Int, encoding: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return false
        val device = usbOutputDevice(audioManager) ?: return false
        val attributes = platformAttributes()
        val mixerAttributes = audioManager.getSupportedMixerAttributes(device)
            .firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                    it.format.sampleRate == sampleRate &&
                    it.format.encoding == encoding
            } ?: return false
        return audioManager.setPreferredMixerAttributes(attributes, device, mixerAttributes)
    }

    /** Release the bit-perfect mixer request when Bit Perfect is turned off or the route changes. */
    @SuppressLint("NewApi")
    fun clearBitPerfectMixer(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val device = usbOutputDevice(audioManager) ?: return
        audioManager.clearPreferredMixerAttributes(platformAttributes(), device)
    }
}
