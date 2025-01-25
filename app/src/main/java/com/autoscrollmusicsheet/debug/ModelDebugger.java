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
            try {
                interpreter.run(inputArray, outputArray);
                Log.d(TAG, "Inference successful!");
                debugInfo.append("Inference completed successfully!\n");

                // Log first few output values
                StringBuilder outputValues = new StringBuilder("First 10 output values: ");
                for (int i = 0; i < Math.min(10, outputArray[0].length); i++) {
                    outputValues.append(outputArray[0][i]).append(" ");
                }
                Log.d(TAG, outputValues.toString());
                debugInfo.append(outputValues);

            } catch (Exception e) {
                Log.e(TAG, "Inference failed", e);
                debugInfo.append("Inference Error: ").append(e.getMessage()).append("\n");
                e.printStackTrace();
            }

            interpreter.close();
            fileDescriptor.close();
            inputStream.close();

            return debugInfo.toString();

        } catch (Exception e) {
            String errorMsg = "Model inspection failed: " + e.getMessage();
            Log.e(TAG, errorMsg, e);
            e.printStackTrace();
            return errorMsg;
        }
    }
}