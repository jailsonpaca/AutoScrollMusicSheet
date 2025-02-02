package com.autoscrollmusicsheet.asr

interface ASRLayer {
    fun loadModel(modelPath: String, vocabPath: String? = null)
    fun setListener(listener: ASRListener)
    fun processAudio(audioData: FloatArray)
    fun close()
}
