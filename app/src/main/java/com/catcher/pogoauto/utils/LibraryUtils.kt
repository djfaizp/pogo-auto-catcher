package com.catcher.pogoauto.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for handling native libraries
 */
object LibraryUtils {
    private const val TAG = "LibraryUtils"

    /**
     * Copy the Frida gadget library from the custom path to the app's native library directory
     * @param context the application context
     * @return true if successful, false otherwise
     */
    fun copyFridaGadgetLibrary(context: Context): Boolean {
        LogUtils.i(TAG, "Copying Frida gadget library to app's native library directory")

        try {
            // Try to extract the Frida gadget library from assets
            val assetManager = context.assets
            val fridaAssetPath = "lib/arm64-v8a/libfrida-gadget.so"

            LogUtils.i(TAG, "Attempting to extract Frida gadget library from assets: $fridaAssetPath")

            // Create a temporary file to hold the extracted library
            val tempFile = File(context.cacheDir, "libfrida-gadget.so")

            try {
                // Copy from assets to the temporary file
                assetManager.open(fridaAssetPath).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                LogUtils.i(TAG, "Successfully extracted Frida gadget library from assets to: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            } catch (e: IOException) {
                LogUtils.w(TAG, "Failed to extract Frida gadget library from assets: ${e.message}")

                // Fallback to checking if the library is already in the APK's lib directory
                val sourceFile = File(context.applicationInfo.nativeLibraryDir, "libfrida-gadget.so")

                if (sourceFile.exists() && sourceFile.length() > 0) {
                    LogUtils.i(TAG, "Found Frida gadget library in native library directory: ${sourceFile.absolutePath}, size: ${sourceFile.length()} bytes")
                    return true // Library is already in the right place
                } else {
                    LogUtils.e(TAG, "Frida gadget library not found in assets or native library directory")
                    return false
                }
            }

            // Use the temporary file as the source
            val sourceFile = tempFile

            LogUtils.i(TAG, "Using extracted Frida gadget library: ${sourceFile.absolutePath}, size: ${sourceFile.length()} bytes")

            // Destination directory - app's native library directory
            val destDir = File(context.applicationInfo.nativeLibraryDir)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }

            // Destination file
            val destFile = File(destDir, "libfrida-gadget.so")

            // Copy the file
            sourceFile.inputStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Verify the copy
            if (destFile.exists() && destFile.length() > 0) {
                LogUtils.i(TAG, "Successfully copied Frida gadget library to: ${destFile.absolutePath}, size: ${destFile.length()} bytes")

                // Set executable permission
                try {
                    val execResult = Runtime.getRuntime().exec("chmod 755 ${destFile.absolutePath}")
                    val exitValue = execResult.waitFor()
                    LogUtils.i(TAG, "Set executable permission on library: $exitValue")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Failed to set executable permission on library", e)
                    // Continue anyway, this might not be necessary
                }

                return true
            } else {
                LogUtils.e(TAG, "Failed to copy Frida gadget library: Destination file does not exist or is empty")
                return false
            }
        } catch (e: IOException) {
            LogUtils.e(TAG, "Error copying Frida gadget library", e)
            return false
        } catch (e: Exception) {
            LogUtils.e(TAG, "Unexpected error copying Frida gadget library", e)
            return false
        }
    }

    /**
     * Check if the Frida gadget library exists in the app's native library directory
     * @param context the application context
     * @return true if the library exists, false otherwise
     */
    fun checkFridaGadgetLibrary(context: Context): Boolean {
        val libFile = File(context.applicationInfo.nativeLibraryDir, "libfrida-gadget.so")
        val exists = libFile.exists() && libFile.length() > 0

        LogUtils.i(TAG, "Frida gadget library check: exists=$exists, path=${libFile.absolutePath}, size=${if (exists) libFile.length() else 0} bytes")

        return exists
    }

    /**
     * Get the path to the Frida gadget library
     * @param context the application context
     * @return the absolute path to the library
     */
    fun getFridaGadgetLibraryPath(context: Context): String {
        return File(context.applicationInfo.nativeLibraryDir, "libfrida-gadget.so").absolutePath
    }

    /**
     * Get the path to the custom Frida gadget library
     * @return the absolute path to the custom library
     */
    fun getCustomFridaGadgetLibraryPath(): String {
        return "/data/local/tmp/libfrida-gadget.so" // Fallback location
    }

    /**
     * Extract the Frida gadget library from assets to a specific location
     * @param context the application context
     * @param outputPath the path to extract the library to
     * @return true if successful, false otherwise
     */
    fun extractFridaGadgetFromAssets(context: Context, outputPath: String): Boolean {
        try {
            val assetManager = context.assets
            val fridaAssetPath = "lib/arm64-v8a/libfrida-gadget.so"

            LogUtils.i(TAG, "Extracting Frida gadget library from assets to: $outputPath")

            // Create output directory if it doesn't exist
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            // Copy from assets to the output file
            assetManager.open(fridaAssetPath).use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Set executable permission
            try {
                val execResult = Runtime.getRuntime().exec("chmod 755 $outputPath")
                val exitValue = execResult.waitFor()
                LogUtils.i(TAG, "Set executable permission on extracted library: $exitValue")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to set executable permission on extracted library", e)
            }

            return outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            LogUtils.e(TAG, "Failed to extract Frida gadget library from assets", e)
            return false
        }
    }
}
