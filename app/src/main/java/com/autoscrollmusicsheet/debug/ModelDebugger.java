package com.autoscrollmusicsheet.debug;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class ModelDebugger {
    private static final String TAG = "TFLite_Debug";

    static {
        try {
            System.loadLibrary("tensorflowlite_jni");
            Log.d(TAG, "TensorFlow Lite native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load TensorFlow Lite native library", e);
        }
    }

    public String debugTFLiteModel(Context context, String modelPath) {
        StringBuilder debugInfo = new StringBuilder();

        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            MappedByteBuffer model = fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.getStartOffset(),
                    fileDescriptor.getDeclaredLength()
            );

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            options.setUseNNAPI(false);

            Log.d(TAG, "Creating interpreter...");
            try {
                Interpreter interpreter = new Interpreter(model, options);
                Log.d(TAG, "Interpreter created successfully");

                // Create input with correct shape [1, 80, 3000]
                float[][][] inputArray = new float[1][80][3000];
                // Fill with small random values between -1 and 1
                for (int i = 0; i < 80; i++) {
                    for (int j = 0; j < 3000; j++) {
                        inputArray[0][i][j] = (float) (Math.random() * 2 - 1) * 0.1f;
                    }
                }

                // Create output array with correct shape [1, 501]
                int[][] outputArray = new int[1][501];

                Log.d(TAG, "Attempting inference with properly shaped data...");
                interpreter.run(inputArray, outputArray);
                Log.d(TAG, "Inference successful!");

                debugInfo.append("Model loaded and tested successfully\n");
                debugInfo.append("Input shape: [1, 80, 3000]\n");
                debugInfo.append("Output shape: [1, 501]\n");

                interpreter.close();
            } catch (Exception e) {
                Log.e(TAG, "Error during interpreter creation/inference", e);
                debugInfo.append("Error during interpreter creation/inference: ").append(e.getMessage());
            }

            inputStream.close();
            fileDescriptor.close();

        } catch (Exception e) {
            Log.e(TAG, "Error loading model", e);
            debugInfo.append("Error loading model: ").append(e.getMessage());
        }

        return debugInfo.toString();
    }
}