package com.autoscrollmusicsheet.asr

import android.content.Context
import android.util.Log
import java.io.File

class ASRManager(private val context: Context) {
    private val layers: MutableList<ASRLayer> = mutableListOf()
    private val layerWeights: MutableMap<ASRLayer, Int> = mutableMapOf()

    fun registerLayer(layer: ASRLayer, weight: Int) {
        layers.add(layer)
        layerWeights[layer] = weight
    }

    fun initializeModels() {
        for (layer in layers) {
            when (layer) {
                is WhisperLayer -> layer.loadModel(getFilePath("whisper-small-pt-v2.tflite"), getFilePath("filters_vocab_multilingual.bin"))
                is VoskLayer -> layer.loadModel(getFilePath("vosk-model-small-pt"))
            }
        }
    }

    fun setListener(listener: ASRListener) {
        layers.forEach { it.setListener(listener) }
    }

    fun processAudio(audioData: FloatArray) {
        layers.sortedByDescending { layerWeights[it] ?: 0 }
            .forEach { it.processAudio(audioData) }
    }

    private fun getFilePath(assetName: String): String {
        return File(context.filesDir, assetName).absolutePath
    }

    fun close() {
        layers.forEach(fun (layer){
            layer.close();
        })
    }
}
