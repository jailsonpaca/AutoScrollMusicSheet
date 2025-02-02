package com.autoscrollmusicsheet.asr

import android.content.Context
import android.util.Log
import com.autoscrollmusicsheet.asr.ASRLayer
import com.autoscrollmusicsheet.asr.ASRListener
import com.autoscrollmusicsheet.asr.IWhisperListener
import com.autoscrollmusicsheet.asr.Whisper
//import com.autoscrollmusicsheet.debug.ModelDebugger
import java.io.File
import java.io.IOException

class WhisperLayer(private val context: Context) : ASRLayer {
    private val TAG = "WhisperLayer"
    private var whisper: Whisper? = null
    private var listener: ASRListener? = null
    private var MODEL_PATH: String = "whisper-small-pt-v2.tflite"

    init {
        try {
            whisper = Whisper(context)
            whisper?.setListener(object : IWhisperListener {
                override fun onUpdateReceived(message: String) {
                    Log.d(TAG, "Whisper update: $message")
                }

                override fun onResultReceived(result: String) {
                    Log.d(TAG, "Whisper result: $result")
                    listener?.onResultReceived(result)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper: ${e.message}")
            listener?.onError("Error initializing Whisper: ${e.message}")
        }
    }

    override fun loadModel(modelPath: String, vocabPath: String?) {
        try {
            val useMultilingual = true
            val actualModelPath: String
            val actualVocabPath: String
            
            if (useMultilingual) {
                actualModelPath = getFilePath(MODEL_PATH)
                actualVocabPath = getFilePath("filters_vocab_multilingual.bin")
            } else {
                actualModelPath = getFilePath("whisper-tiny-en.tflite")
                actualVocabPath = getFilePath("filters_vocab_en.bin")
            }
            
            whisper?.loadModel(actualModelPath, actualVocabPath, useMultilingual)
            Log.d(TAG, "Whisper model loaded from: $actualModelPath")
            // logModel()
        } catch (e: Exception) {
            val error = "Error loading Whisper model: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            throw e
        }
    }

    override fun setListener(listener: ASRListener) {
        this.listener = listener
        Log.d(TAG, "Listener set for Whisper")
    }

    override fun processAudio(audioData: FloatArray) {
        try {
            whisper?.writeBuffer(audioData)
        } catch (e: Exception) {
            val error = "Error processing audio: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            e.printStackTrace()
        }
    }

    override fun close() {
        try {
            whisper?.stop()
            whisper = null
            Log.d(TAG, "Whisper resources released")
        } catch (e: Exception) {
            val error = "Error closing Whisper resources: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            e.printStackTrace()
        }
    }

    private fun getFilePath(assetName: String): String {
        val outfile = File(context.getExternalFilesDir(null), assetName)
        if (!outfile.exists()) {
            val error = "File not found - ${outfile.absolutePath}"
            Log.e(TAG, error)
            listener?.onError(error)
        }
        Log.d(TAG, "Returned asset path: ${outfile.absolutePath}")
        return outfile.absolutePath
    }

  /*  private fun logModel() {
        try {
            val debugger = ModelDebugger()
            val debugInfo = debugger.debugTFLiteModel(
                context,
                MODEL_PATH
            )
            Log.d(TAG, "Model Debug Info:\n$debugInfo")
        } catch (e: Exception) {
            val error = "Failed to debug model: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
        }
    }*/
}
