package com.au.launcher.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object SoundHelper {
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    private val loadedSounds = mutableSetOf<Int>()

    fun init(context: Context) {
        if (soundPool != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds.add(sampleId)
            }
        }

        loadSound(context, "confirm")
        loadSound(context, "cancel")
        loadSound(context, "btn_switch")
        loadSound(context, "save")
    }

    private fun loadSound(context: Context, name: String) {
        val resId = context.resources.getIdentifier(name, "raw", context.packageName)
        if (resId != 0) {
            val sampleId = soundPool?.load(context, resId, 1) ?: -1
            if (sampleId != -1) {
                soundIds[name] = sampleId
            }
        }
    }

    fun playClick() = play("confirm")
    fun playConfirm() = play("confirm")
    fun playCancel() = play("cancel")
    fun playSwitch() = play("btn_switch")
    fun playSave() = play("save")

    private fun play(name: String) {
        val id = soundIds[name] ?: return
        if (loadedSounds.contains(id)) {
            soundPool?.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        loadedSounds.clear()
    }
}
