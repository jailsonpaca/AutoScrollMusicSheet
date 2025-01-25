package com.whispertflite;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.whispertflite.asr.IRecorderListener;
import com.whispertflite.asr.IWhisperListener;
import com.whispertflite.debug.ModelDebugger;
import com.whispertflite.utils.WaveUtil;
import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";

    private TextView tvStatus;
    private TextView tvResult;

    private Whisper mWhisper = null;
    private Recorder mRecorder = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            ModelDebugger debugger = new ModelDebugger();
            String debugInfo = debugger.debugTFLiteModel(
                    this,
                    "whisper-small-pt-v2.tflite"  // Your model path in assets
            );
            Log.d(TAG, "Model Debug Info:\n" + debugInfo);
        } catch (Exception e) {
            Log.e(TAG, "Failed to debug model", e);
        }

        final String[] waveFileName = {null};
        final Handler handler = new Handler(Looper.getMainLooper());

        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);

        // Implementation of record button functionality
        Button btnMicRec = findViewById(R.id.btnRecord);
        btnMicRec.setOnClickListener(v -> {
            if (mRecorder != null && mRecorder.isInProgress()) {
                Log.d(TAG, "Recording is in progress... stopping...");
                stopRecording();
            } else {
                Log.d(TAG, "Start recording...");
                startRecording();
            }
        });

        // Implementation of file spinner functionality
        ArrayList<String> files = new ArrayList<>();
        // files.add(WaveUtil.RECORDING_FILE);
        try {
            String[] assetFiles = getAssets().list("");
            for (String file : assetFiles) {
                if (file.endsWith(".wav")) files.add(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Call the method to copy specific file types from assets to data folder
        String[] extensionsToCopy = {"pcm", "bin", "wav", "tflite"};
        copyAssetsWithExtensionsToDataFolder(this, extensionsToCopy);


        String modelPath;
        String vocabPath;
        /* English-only model and vocab */
        modelPath = getFilePath("whisper-small-pt-v2.tflite");
        vocabPath = getFilePath("filters_vocab_multilingual.bin");

        mWhisper = new Whisper(this);
        mWhisper.loadModel(modelPath, vocabPath, true);
        mWhisper.setListener(new IWhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "mWhisper onUpdateReceived: " + message);
                handler.post(() -> tvStatus.setText(message));

                if (message.equals(Whisper.MSG_PROCESSING)) {
                    handler.post(() -> tvResult.setText(""));
                } else if (message.equals(Whisper.MSG_FILE_NOT_FOUND)) {
                    // write code as per need to handled this error
                    Log.d(TAG, "File not found error...!");
                }
            }

            @Override
            public void onResultReceived(String result) {
                Log.d(TAG, "mRecorder onResultReceived: " + result);
                handler.post(() -> tvResult.append(result));
            }
        });

        mRecorder = new Recorder(this);
        mRecorder.setListener(new IRecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                Log.d(TAG, "mRecorder onUpdateReceived: " + message);
                handler.post(() -> tvStatus.setText(message));

                if (message.equals(Recorder.MSG_RECORDING)) {
                    handler.post(() -> tvResult.setText(""));
                    handler.post(() -> btnMicRec.setText(Recorder.ACTION_STOP));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    handler.post(() -> btnMicRec.setText(Recorder.ACTION_RECORD));
                }
            }

            @Override
            public void onDataReceived(float[] samples) {
                mWhisper.writeBuffer(samples);
            }
        });

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission();

    }

    private void checkRecordPermission() {
        int permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        } else {
            Log.d(TAG, "Requesting record permission");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            Log.d(TAG, "Record permission is granted");
        else Log.d(TAG, "Record permission is not granted");
    }

    // Recording calls
    private void startRecording() {
        checkRecordPermission();

        String waveFilePath = getFilePath(WaveUtil.RECORDING_FILE);
        mRecorder.setFilePath(waveFilePath);
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    // Copy assets to data folder
    private static void copyAssetsWithExtensionsToDataFolder(Context context, String[] extensions) {
        AssetManager assetManager = context.getAssets();
        try {
            // Specify the destination directory in the app's data folder
            String destFolder = context.getFilesDir().getAbsolutePath();

            for (String extension : extensions) {
                // List all files in the assets folder with the specified extension
                String[] assetFiles = assetManager.list("");
                for (String assetFileName : assetFiles) {
                    if (assetFileName.endsWith("." + extension)) {
                        File outFile = new File(destFolder, assetFileName);
                        if (outFile.exists()) continue;

                        InputStream inputStream = assetManager.open(assetFileName);
                        OutputStream outputStream = new FileOutputStream(outFile);

                        // Copy the file from assets to the data folder
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }

                        inputStream.close();
                        outputStream.flush();
                        outputStream.close();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Returns file path from data folder
    private String getFilePath(String assetName) {
        File outfile = new File(getFilesDir(), assetName);
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.getAbsolutePath());
        }

        Log.d(TAG, "Returned asset path: " + outfile.getAbsolutePath());
        return outfile.getAbsolutePath();
    }
}