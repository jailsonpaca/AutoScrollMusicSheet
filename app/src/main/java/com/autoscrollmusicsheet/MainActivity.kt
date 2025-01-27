package com.autoscrollmusicsheet

import android.Manifest
import android.content.Context
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
import com.autoscrollmusicsheet.asr.IRecorderListener
import com.autoscrollmusicsheet.asr.IWhisperListener
import com.autoscrollmusicsheet.asr.Recorder
import com.autoscrollmusicsheet.asr.Whisper
import com.autoscrollmusicsheet.utils.WaveUtil
import com.autoscrollmusicsheet.debug.ModelDebugger;
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var mWhisper: Whisper? = null
    private var mRecorder: Recorder? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val audioTextMatcher = AudioTextMatcher()

    private lateinit var contentView: TextView
    private var resultView: TextView? = null
    private lateinit var recordButton: Button

    private val STATE_STARTING: Int = 0
    private val STATE_READY: Int = 1
    private val STATE_RECORDING: Int = 2
    private var CURRENT_STATE: Int? = null

    private val PERMISSIONS_REQUEST_RECORD_AUDIO: Int = 1

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
        initializeViews()
        mWhisper = Whisper(this)
        mRecorder = Recorder(this)

        // logModel()
        // Assume this Activity is the current activity, check record permission
        checkRecordPermissionAndPrepare()
    }
    private fun logModel() {
        try {
            val debugger = ModelDebugger()
            val debugInfo = debugger.debugTFLiteModel(
                this,
                "whisper-small-pt-v2.tflite" // Your model path in assets
            )
            Log.d(TAG, "Model Debug Info:\n$debugInfo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to debug model", e)
        }
    }

    private fun initializeViews() {
        contentView = findViewById(R.id.contentView)
        resultView = findViewById<TextView>(R.id.result_text)
        recordButton = findViewById(R.id.recordButton)
        setUiState(STATE_STARTING)
        recordButton.setOnClickListener { view: View? ->  initializeRecognizer() }

        updateDisplay(0)
    }

    private fun checkRecordPermissionAndPrepare() {
        val permissionCheck =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSIONS_REQUEST_RECORD_AUDIO
            )
        } else {
            initModel()
        }
    }

    private fun initModel() {
        val modelPath: String
        val vocabPath: String
        val useMultilingual = false
        if (useMultilingual) {
            // Multilingual model and vocab
            modelPath = getFilePath("whisper-tiny.tflite")
            vocabPath = getFilePath("filters_vocab_multilingual.bin")
        } else {
            // English-only model and vocab
            modelPath = getFilePath("whisper-tiny-en.tflite")
            vocabPath = getFilePath("filters_vocab_en.bin")
        }
        mWhisper?.loadModel(modelPath, vocabPath, useMultilingual)
    }
    private fun initializeRecognizer() {
        if(CURRENT_STATE == STATE_RECORDING){
            mWhisper?.stop();
            mRecorder?.stop();
            setUiState(STATE_READY)
        }else {
            try {
                val waveFilePath: String = getFilePath(WaveUtil.RECORDING_FILE)
                mRecorder!!.setListener(object : IRecorderListener {
                    override fun onUpdateReceived(message: String) {
                        Log.d(TAG, "Update is received, Message: $message")
                        // handler.post { tvStatus.setText(message)
                    }

                    override fun onDataReceived(samples: FloatArray) {
                        //mWhisper.writeBuffer(samples);
                    }
                })
                mRecorder!!.setFilePath(waveFilePath)
                mRecorder!!.start()
                mWhisper?.setListener(object : IWhisperListener {
                    override fun onUpdateReceived(message: String?) {
                        TODO("Not yet implemented")
                    }

                    override fun onResultReceived(result: String) {
                        Log.d(TAG, "onResultReceived: $result")
                        handler.post { resultView?.append(result) }
                        processResult(result)
                    }
                })
                setUiState(STATE_RECORDING)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel()
            } else {
                finish()
            }
        }
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
    // Returns file path from data folder
    private fun getFilePath(assetName: String): String {
        val outfile = File(filesDir, assetName)
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.absolutePath)
        }

        Log.d(TAG, "Returned asset path: " + outfile.absolutePath)
        return outfile.absolutePath
    }

    public override fun onDestroy() {
        super.onDestroy()

        mWhisper?.stop();
        mRecorder?.stop();
    }
}