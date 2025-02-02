package com.autoscrollmusicsheet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autoscrollmusicsheet.asr.ASRListener
import com.autoscrollmusicsheet.asr.ASRManager
import com.autoscrollmusicsheet.asr.IRecorderListener
import com.autoscrollmusicsheet.asr.Recorder
import com.autoscrollmusicsheet.asr.VoskLayer
import com.autoscrollmusicsheet.asr.Whisper
import com.autoscrollmusicsheet.asr.WhisperLayer
import org.tensorflow.lite.TensorFlowLite

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var asrManager: ASRManager
    private var mWhisper: Whisper? = null
    private var mRecorder: Recorder? = null
    private val audioTextMatcher = AudioTextMatcher()

    private lateinit var contentView: TextView
    private var resultView: TextView? = null
    private lateinit var recordButton: Button

    private val STATE_STARTING: Int = 0
    private val STATE_READY: Int = 1
    private val STATE_RECORDING: Int = 2
    private var CURRENT_STATE: Int? = null

    private val PERMISSIONS_REQUEST_RECORD_AUDIO: Int = 1
    companion object {
        private val TAG = "MainActivity"

        init {
            try {
                System.loadLibrary("tensorflowlite_jni")
                Log.d(TAG, "TensorFlow Lite native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load TensorFlow Lite native library", e)
            }
        }
    }

    private val content = """
        Mudam-se os tempos, mudam-se as vontades,
        Muda-se o ser, muda-se a confiança;
        Todo o mundo é composto de mudança,
        Tomando sempre novas qualidades.    
        Continuamente vemos novidades,
        Diferentes em tudo da esperança;
        Do mal ficam as mágoas na lembrança,
        E do bem, se algum houve, as saudades.
        O tempo cobre o chão de verde manto,
        Que já coberto foi de neve fria,
        E em mim converte em choro o doce canto.
        E, afora este mudar-se cada dia,
        Outra mudança faz de mor espanto:
        Que não se muda já como soía.
    """.trimIndent().split("\n")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Skip TensorFlow Lite initialization here since we're handling it in the native code
        initializeViews()
        checkRecordPermissionAndPrepare()
    }
   
    private fun initializeViews() {
        contentView = findViewById(R.id.contentView)
        resultView = findViewById<TextView>(R.id.result_text)
        recordButton = findViewById(R.id.recordButton)
        setUiState(STATE_STARTING)
        recordButton.setOnClickListener { toggleRecording() }
        updateDisplay(0)
    }

    private fun toggleRecording() {
        when (CURRENT_STATE) {
            STATE_RECORDING -> {
                stopRecording()
                setUiState(STATE_READY)
            }
            else -> {
                startRecording()
                setUiState(STATE_RECORDING)
            }
        }
    }

    private fun startRecording() {
        try {
            mRecorder?.start()
            Log.d(TAG, "Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            resultView?.text = "Error starting recording: ${e.message}"
        }
    }

    private fun stopRecording() {
        try {
            mRecorder?.stop()
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            resultView?.text = "Error stopping recording: ${e.message}"
        }
    }

    private fun checkRecordPermissionAndPrepare() {
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            prepareAudioProcessing()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prepareAudioProcessing()
            } else {
                resultView?.text = "Audio recording permission is required for this app to work"
            }
        }
    }

    private fun prepareAudioProcessing() {
        try {
            Log.d(TAG, "Initializing ASR models...")

            // Initialize ASR Manager and layers
            asrManager = ASRManager(this)
            asrManager.registerLayer(WhisperLayer(this), 2) // Whisper has higher weight
            asrManager.registerLayer(VoskLayer(this), 1)    // Vosk has lower weight
            asrManager.initializeModels()

            // Initialize Recorder
            mRecorder = Recorder(this)
            mRecorder?.setListener(object : IRecorderListener {
                override fun onUpdateReceived(message: String) {
                    Log.d(TAG, "Recorder update: $message")
                }

                override fun onDataReceived(samples: FloatArray) {
                    asrManager.processAudio(samples)
                }
            })

            asrManager.setListener(object : ASRListener {
                override fun onResultReceived(result: String) {
                    if(CURRENT_STATE == STATE_RECORDING) {
                        handleRecognitionResult(result)
                    }
                }

                override fun onError(error: String) {
                    Log.e(TAG, "ASR Error: $error")
                    runOnUiThread {
                        resultView?.text = "Error: $error"
                    }
                }
            })

            setUiState(STATE_READY)
            Log.d(TAG, "ASR models are ready.")
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing audio processing", e)
            runOnUiThread {
                resultView?.text = "Error preparing audio: ${e.message}"
            }
        }
    }

    private fun handleRecognitionResult(result: String) {
        Log.d(TAG, "ASR Result: $result")
        runOnUiThread {
            resultView?.append("$result\n")
        }
        processResult(result)
    }

    private fun updateDisplay(position: Int) {
        runOnUiThread {
            val visibleLines = content.slice(position until minOf(position + 40, content.size))
            contentView.text = visibleLines.joinToString("\n")
        }
    }
    
    private fun setUiState(state: Int) {
        CURRENT_STATE=state
        when (state) {
            STATE_STARTING -> {
                resultView?.setText(R.string.preparing)
                resultView!!.movementMethod = ScrollingMovementMethod()
            }

            STATE_READY -> {
                recordButton.setText(R.string.start_recording)
                resultView?.setText(R.string.ready)
            }

            STATE_RECORDING -> {
                recordButton.setText(R.string.stop_recording)
                resultView!!.text = getString(R.string.say_something)
            }

            else -> throw IllegalStateException("Unexpected value: $state")
        }
    }

    private fun processResult(result: String) {
        try {

            if (result.isNotEmpty()) {
                resultView?.append(result + "\n");
                val matchResult = audioTextMatcher.findBestMatch(
                    audioTextMatcher.preprocessText(result),
                    content
                )

                if (matchResult.score > AudioTextMatcher.MATCH_THRESHOLD) {
                    updateDisplay(matchResult.position)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        try {
            mRecorder?.stop()
            asrManager.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
        }
    }
}