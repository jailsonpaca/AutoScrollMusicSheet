package com.autoscrollmusicsheet.asr

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer as VoskRecognizer
import java.io.File

class VoskLayer(private val context: Context) : ASRLayer {
    private val TAG = "VoskLayer"
    private var recognizer: VoskRecognizer? = null
    private var model: Model? = null
    private var listener: ASRListener? = null

    init {
        try {
            val modelPath = context.filesDir.absolutePath + "/vosk-model-small-pt"
            copyModelFiles(modelPath)
            loadModel(modelPath, null)
            Log.d(TAG, "Vosk model loaded successfully")
        } catch (e: Exception) {
            val error = "Error initializing Vosk model: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            e.printStackTrace()
        }
    }

    override fun loadModel(modelPath: String, vocabPath: String?) {
        try {
            model?.close()
            recognizer?.close()
            
            model = Model(modelPath)
            recognizer = VoskRecognizer(model, 16000.0f)
            Log.d(TAG, "Vosk model loaded from: $modelPath")
        } catch (e: Exception) {
            val error = "Error loading Vosk model: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            throw e
        }
    }

    override fun setListener(listener: ASRListener) {
        this.listener = listener
        Log.d(TAG, "Listener set for Vosk")
    }

    override fun processAudio(audioData: FloatArray) {
        try {
            recognizer?.let { rec ->
                if (rec.acceptWaveForm(audioData, audioData.size)) {
                    val result = rec.finalResult
                    listener?.onResultReceived(result ?: "")
                } else {
                    val partialResult = rec.partialResult
                    listener?.onResultReceived(partialResult ?: "")
                }
            }
        } catch (e: Exception) {
            val error = "Error processing audio: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            e.printStackTrace()
        }
    }

    override fun close() {
        try {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            Log.d(TAG, "Vosk resources released")
        } catch (e: Exception) {
            val error = "Error closing Vosk resources: ${e.message}"
            Log.e(TAG, error)
            listener?.onError(error)
            e.printStackTrace()
        }
    }

    private fun copyModelFiles(targetDir: String) {
        try {
            val targetFile = File(targetDir)
            if (!targetFile.exists()) {
                targetFile.mkdirs()
                
                // Copy files from the vosk-model-small-pt-0.3 subdirectory
                val modelSubdir = "vosk-model-small-pt/vosk-model-small-pt-0.3"
                context.assets.list(modelSubdir)?.forEach { filename ->
                    if (filename != "ivector") {  // Handle regular files
                        val inputStream = context.assets.open("$modelSubdir/$filename")
                        val outputFile = File(targetDir, filename)
                        
                        inputStream.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Copied model file: $filename")
                    } else {  // Handle ivector directory
                        val ivectorDir = File(targetDir, "ivector")
                        ivectorDir.mkdirs()
                        
                        context.assets.list("$modelSubdir/ivector")?.forEach { ivectorFile ->
                            val inputStream = context.assets.open("$modelSubdir/ivector/$ivectorFile")
                            val outputFile = File(ivectorDir, ivectorFile)
                            
                            inputStream.use { input ->
                                outputFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Copied ivector file: $ivectorFile")
                        }
                    }
                }
                Log.d(TAG, "Model files copied successfully to: $targetDir")
            } else {
                Log.d(TAG, "Model files already exist at: $targetDir")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying model files", e)
            throw e
        }
    }
}