package com.github.jing332.tts_server_android.model.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class ITextToSpeechSynthesizer<T> {
    suspend fun <T> retry(
        times: Int = 3,
        initialDelayMillis: Long = 200,
        factor: Float = 2F,
        maxDelayMillis: Long = 5000,
        onCatch: suspend (times: Int, t: Throwable) -> Boolean,
        block: suspend () -> T?,
    ): T? {
        var currentDelay = initialDelayMillis
        for (i in 1..times) {
            return try {
                block()
            } catch (t: Throwable) {
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMillis)
                if (onCatch.invoke(i, t)) continue
                else null
            }
        }
        return null
    }

    open fun load() {}
    open fun stop() {}
    open fun destroy() {}

    open suspend fun handleText(text: String): List<TtsText<T>> = emptyList()

    open suspend fun getAudio(tts: T, text: String, sysRate: Int, sysPitch: Int): ByteArray? = null

    private suspend fun handleTextAndToSpeech(
        text: String,
        sysRate: Int,
        sysPitch: Int,
        onAudioAvailable: suspend (AudioData<T>) -> Unit
    ) {
        val channel = Channel<AudioData<T>>(10)

        coroutineScope {
            launch(Dispatchers.IO) {
                val textList = handleText(text)
                textList.forEach { subTxtTts ->
                    val audio = getAudio(subTxtTts.tts, subTxtTts.text, sysRate, sysPitch)
                    channel.send(AudioData(txtTts = subTxtTts, audio = audio))
                }
                channel.close()
            }

            for (data in channel) {
                onAudioAvailable.invoke(data)
            }
        }
    }

    suspend fun synthesizeText(
        text: String,
        sysRate: Int,
        sysPitch: Int,
        onOutput: suspend (audio: ByteArray?, txtTts: TtsText<T>) -> Unit,
    ) {
        handleTextAndToSpeech(text, sysRate, sysPitch) {
            onOutput.invoke(it.audio, it.txtTts)
        }
    }

    @Suppress("ArrayInDataClass")
    data class AudioData<T>(
        val txtTts: TtsText<T>,
        val audio: ByteArray? = null,
        val isCancelled: Boolean = false
    )

}