
package com.example.mfa.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import com.russhwolf.settings.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.lang.Math.sqrt
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.security.auth.callback.Callback

class SupabaseService(private val supabase: SupabaseClient) {

    // Function to register a new user
    suspend fun registerUser(
        fullName: String,
        email: String,
        password: String,
        capturedImageBytes: ByteArray?
    ): Boolean {
        return try {
            // ✅ Step 1: Upload Profile Image (if provided)

            // ✅ Step 2: Store User Data in Supabase Database FIRST
            val hashedPassword = hashPassword(password)  // Secure password storage
            val imageBase64 = capturedImageBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.DEFAULT) }

            val userData = mapOf(
                "fullname" to fullName,
                "email" to email,
                "password" to hashedPassword,
                "image_url" to imageBase64
            )

            val insertResponse = supabase.from("users").insert(userData) // Standard insertion
            Log.d("SupabaseService", "User data inserted into database successfully: $insertResponse")

            // ✅ Step 3: Register User in Supabase Auth (After Data Insert)
            val authResponse = supabase.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            if (authResponse != null) {
                Log.d("SupabaseService", "User Authentication Failed")
                false
            } else {
                Log.e("SupabaseService", "User authentication Success.")
                true
            }
        } catch (e: Exception) {
            Log.e("SupabaseService", "Error registering user: ${e.message}")
            false
        }
    }



    // Function to upload an image to Supabase Storage


    // Function to hash the password securely
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP) // ✅ Fixed Base64 encoding
    }

    // Function to login user
    suspend fun loginUser(email: String, password: String): Boolean {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Log.d("SupabaseService", "User logged in successfully: $email")
            true
        } catch (e: Exception) {
            Log.e("SupabaseService", "Login failed: ${e.message}")
            false
        }
    }

    suspend fun sendEmailOTP(email: String): Boolean {
        return try {
            // Send email OTP using standard Supabase auth
            supabase.auth.signInWith(OTP){
                this.email=email
            }
            Log.d("SupabaseService", "OTP sent successfully to $email")
            true
        } catch (e: Exception) {
            Log.e("SupabaseService", "Failed to send OTP: ${e.message}")
            false
        }
    }

    suspend fun verifyEmailOTP(email: String, otp: String ): Boolean {
        return try {
            // Verify email OTP
            supabase.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL,
                email = email,
                token = otp
            )
            Log.d("SupabaseService", "OTP verified successfully for $email")
            true
        } catch (e: Exception) {
            Log.e("SupabaseService", "OTP verification failed: ${e.message}")
            false
        }
    }




    fun fetchStoredImageBytes(userEmail: String, onResult: (ByteArray?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("ImageFetch", "Fetching image for email: $userEmail") // Debug log

                if (userEmail.isEmpty()) {
                    Log.e("ImageFetch", "User email is empty") // Error log
                    withContext(Dispatchers.Main) { onResult(null) }
                    return@launch
                }

                // Fetch the image_url from Supabase
                val response = supabase.from("users")
                    .select(columns = Columns.list("image_url")) {
                        filter {
                            eq("email", userEmail)
                        }
                    }
                    .decodeSingleOrNull<Map<String, String?>>()

                Log.d("ImageFetch", "Supabase Response: $response") // Debug log

                val imageHex = response?.get("image_url")

                if (!imageHex.isNullOrEmpty()) {
                    Log.d("ImageFetch", "Image Data Found: $imageHex")

                    // Convert HEX or JSON Buffer to ByteArray
                    val imageBytes = convertToByteArray(imageHex)
                    withContext(Dispatchers.Main) { onResult(imageBytes) }
                } else {
                    Log.e("ImageFetch", "No stored image found for user!")
                    withContext(Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                Log.e("ImageFetch", "Error fetching image: ${e.message}")
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }
    fun convertToByteArray(imageHex: String): ByteArray {
        return when {
            imageHex.startsWith("\\x") -> {
                // Convert HEX (\x...) to ByteArray
                imageHex.substring(2).chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
            }
            imageHex.contains("type") && imageHex.contains("data") -> {
                val jsonData = imageHex.substringAfter("data\":[").substringBefore("]").split(",")
                jsonData.map { it.trim().toInt().toByte() }.toByteArray()
            }
            else -> {
                try {
                    Base64.decode(imageHex, Base64.DEFAULT)
                } catch (e: IllegalArgumentException) {
                    Log.e("ImageFetch", "Invalid Base64 input")
                    ByteArray(0) // Return empty byte array on error
                }
            }
        }
    }

    fun loadFaceNetModel(filename: String, context: Context): Interpreter? {
        return try {
            val assetFileDescriptor = context.assets.openFd(filename)
            val fileInputStream = assetFileDescriptor.createInputStream()
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val compatList = CompatibilityList()
            val options = Interpreter.Options().apply {
                if (compatList.isDelegateSupportedOnThisDevice) {
                    addDelegate(GpuDelegate(compatList.bestOptionsForThisDevice))
                } else {
                    setNumThreads(4)
                }
            }
            Interpreter(modelBuffer, options)
        } catch (e: IOException) {
            Log.e("FaceNet", "Error loading FaceNet model: ${e.message}")
            null
        }
    }

    fun preprocessFace(bitmap: Bitmap): Bitmap {
        val targetSize = 112
        val grayBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBitmap)
        val paint = Paint().apply { isFilterBitmap = true }

        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)  // Convert to grayscale
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Resize and normalize
        val resizedBitmap = Bitmap.createScaledBitmap(grayBitmap, targetSize, targetSize, true)

        return resizedBitmap
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 112
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            byteBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f * 2 - 1)  // Red
            byteBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f * 2 - 1)   // Green
            byteBuffer.putFloat((pixel and 0xFF) / 255.0f * 2 - 1)          // Blue
        }

        return byteBuffer
    }
    fun extractFaceNetEmbeddings(bitmap: Bitmap, interpreter: Interpreter): FloatArray {
        val inputBuffer = convertBitmapToByteBuffer(bitmap)
        val outputEmbeddings = Array(1) { FloatArray(192) }

        interpreter.run(inputBuffer, outputEmbeddings)
        return outputEmbeddings[0]
    }

    fun calculateCosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        var dotProduct = 0.0f
        var magnitude1 = 0.0f
        var magnitude2 = 0.0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            magnitude1 += embedding1[i] * embedding1[i]
            magnitude2 += embedding2[i] * embedding2[i]
        }

        return if (magnitude1 == 0.0f || magnitude2 == 0.0f) {
            0.0f
        } else {
            (dotProduct / (sqrt(magnitude1.toDouble()) * sqrt(magnitude2.toDouble()))).toFloat()
        }
    }
    fun calculateEuclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0.0f
        for (i in embedding1.indices) {
            sum += (embedding1[i] - embedding2[i]) * (embedding1[i] - embedding2[i])
        }
        return sqrt(sum.toDouble()).toFloat() // Convert to Double first, then back to Float
    }
    fun isFaceMatch(embedding1: FloatArray, embedding2: FloatArray, cosineThreshold: Float = 0.75f, euclideanThreshold: Float = 0.8f): Boolean {
        val cosineSimilarity = calculateCosineSimilarity(embedding1, embedding2)
        val euclideanDistance = calculateEuclideanDistance(embedding1, embedding2)

        return cosineSimilarity > cosineThreshold && euclideanDistance < euclideanThreshold
    }

    fun loadDeepfakeModel(modelFileName: String, context: Context): Interpreter? {
        return try {
            Log.d("DeepfakeModel", "Starting model loading process...")

            val assetFileDescriptor = context.assets.openFd(modelFileName)
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val modelBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFileDescriptor.startOffset,
                assetFileDescriptor.declaredLength
            )

            Log.d("DeepfakeModel", "Model file successfully mapped into memory.")

            val options = Interpreter.Options()
            options.setNumThreads(4) // Use 4 threads for better performance
            options.setUseNNAPI(false) // Explicitly disable NNAPI (GPU acceleration)

            var interpreter: Interpreter? = null

            try {
                // Attempt to use GPU delegate
//                val gpuDelegate = GpuDelegate()
////                options.addDelegate(gpuDelegate)
                Log.d("DeepfakeModel", "Using GPU delegate for acceleration.")

                interpreter = Interpreter(modelBuffer, options)

            } catch (e: Exception) {
                Log.e("DeepfakeModel", "GPU delegate failed: ${e.message}. Falling back to CPU.")

                // Remove GPU delegate and retry with only CPU
                val cpuOptions = Interpreter.Options()
                cpuOptions.setNumThreads(4)
                cpuOptions.setUseNNAPI(false) // Ensure NNAPI remains disabled

                interpreter = Interpreter(modelBuffer, cpuOptions)
            }

            Log.d("DeepfakeModel", "Model loaded successfully.")
            interpreter

        } catch (e: Exception) {
            Log.e("DeepfakeModel", "Failed to load model: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3) // 4 bytes per float, 3 channels (RGB)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(224 * 224)
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

        for (pixelValue in intValues) {
            val r = (pixelValue shr 16 and 0xFF) / 255.0f
            val g = (pixelValue shr 8 and 0xFF) / 255.0f
            val b = (pixelValue and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        return byteBuffer
    }

    fun runDeepfakeModel(model: Interpreter, image: Bitmap): Float {
        val inputShape = intArrayOf(1, 224, 224, 3)
        val outputShape = intArrayOf(1, 1)

        val inputBuffer = preprocessImage(image)
        val outputBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()) // Single float output
        Log.d("DeepfakeModel", "Sending image to the deepfake model for verification...")
        model.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val result = outputBuffer.float
        val confidencePercentage = (result * 100).toInt()

        Log.d("DeepfakeModel", "Model prediction: $result (Confidence: $confidencePercentage%)")

        return confidencePercentage.toFloat() // Threshold for deepfake detection
    }
}
