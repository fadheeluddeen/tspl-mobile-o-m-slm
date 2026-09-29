package com.technavious.om15.ai

import android.content.Context
import android.util.Log
import java.io.File

class LlamaEngine {
    companion object {
        private const val TAG = "LlamaEngine"
        private var loaded = false

        init {
            try {
                System.loadLibrary("om15_llama")
                loaded = true
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                loaded = false
            }
        }

        fun isNativeLoaded(): Boolean = loaded

        fun getModelDir(context: Context): File {
            return File(context.getExternalFilesDir(null), "models").also { it.mkdirs() }
        }

        fun getModelPath(context: Context): String {
            return File(getModelDir(context), "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf").absolutePath
        }

        fun getMmProjPath(context: Context): String {
            return File(getModelDir(context), "mmproj-Qwen2.5-VL-3B-Instruct-f16.gguf").absolutePath
        }

        fun areModelsPresent(context: Context): Boolean {
            return File(getModelPath(context)).exists() && File(getMmProjPath(context)).exists()
        }
    }

    external fun loadModel(modelPath: String, mmProjPath: String): Boolean
    external fun runInference(imagePath: String, prompt: String): String
    external fun freeModel()
    external fun isLoaded(): Boolean
}
