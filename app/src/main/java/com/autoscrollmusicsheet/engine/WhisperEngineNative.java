package com.autoscrollmusicsheet.engine;

import android.util.Log;

import com.autoscrollmusicsheet.asr.IWhisperListener;

public class WhisperEngineNative implements IWhisperEngine {
    static {
        try {
            System.loadLibrary("audioEngine");
            System.loadLibrary("tensorflowlite_jni");
            Log.d("WhisperEngineNative", "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e("WhisperEngineNative", "Failed to load native libraries", e);
            throw e;
        }
    }

    private final String TAG = "WhisperEngineNative";
    private final long nativePtr; // Native pointer to the TFLiteEngine instance

    private boolean mIsInitialized = false;
    private IWhisperListener mUpdateListener = null;

    public WhisperEngineNative() {
        try {
            nativePtr = createTFLiteEngine();
            Log.d(TAG, "TFLite engine created successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to create TFLite engine", e);
            throw e;
        }
    }

    @Override
    public void setUpdateListener(IWhisperListener listener) {
        mUpdateListener = listener;
    }

    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    @Override
    public boolean initialize(String modelPath, String vocabPath, boolean multilingual) {
        try {
            int ret = loadModel(nativePtr, modelPath, multilingual);
            if (ret != 0) {
                Log.e(TAG, "Failed to load model: " + modelPath);
                return false;
            }
            Log.d(TAG, "Model loaded successfully: " + modelPath);
            mIsInitialized = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing model", e);
            return false;
        }
    }

    @Override
    public String transcribeBuffer(float[] samples) {
        if (!mIsInitialized) {
            Log.e(TAG, "Engine not initialized");
            return "";
        }
        try {
            return transcribeBuffer(nativePtr, samples);
        } catch (Exception e) {
            Log.e(TAG, "Error transcribing buffer", e);
            return "";
        }
    }

    @Override
    public String transcribeFile(String waveFile) {
        if (!mIsInitialized) {
            Log.e(TAG, "Engine not initialized");
            return "";
        }
        try {
            return transcribeFile(nativePtr, waveFile);
        } catch (Exception e) {
            Log.e(TAG, "Error transcribing file", e);
            return "";
        }
    }

    @Override
    public void interrupt() {

    }

    @Override
    public void close() {
        if (nativePtr != 0) {
            try {
                freeModel(nativePtr);
                Log.d(TAG, "Model freed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error freeing model", e);
            }
        }
    }

    public void updateStatus(String message) {
        if (mUpdateListener != null)
            mUpdateListener.onUpdateReceived(message);
    }

    // Native methods
    private native long createTFLiteEngine();
    private native int loadModel(long nativePtr, String modelPath, boolean isMultilingual);
    private native String transcribeBuffer(long nativePtr, float[] samples);
    private native String transcribeFile(long nativePtr, String waveFile);
    private native void freeModel(long nativePtr);
}
