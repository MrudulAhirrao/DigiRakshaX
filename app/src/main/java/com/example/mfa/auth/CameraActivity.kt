package com.example.mfa.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.mfa.BuildConfig
import com.example.mfa.MainActivity
import com.example.mfa.R
import com.example.mfa.services.SupabaseService
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import okhttp3.Response
import org.json.JSONArray
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt

val subase = createSupabaseClient(
    supabaseUrl = "https://jisunuvrdgpwxemwkdrh.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imppc3VudXZyZGdwd3hlbXdrZHJoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg4NjUwNDAsImV4cCI6MjA1NDQ0MTA0MH0.W_xSAkPwA4O1alTI_YQqHQoNwUQWb3-VxkewR6Qc4GY"
) {
    install(Postgrest)
    install(Auth)
}
class CameraActivity : AppCompatActivity() {
    private lateinit var textureView: TextureView
    private lateinit var capturedButton: ImageButton
    private lateinit var retakeButton: MaterialButton
    private lateinit var confirmButton: MaterialButton
    private lateinit var capturedImage : ShapeableImageView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture : ImageCapture? = null

    private var attemptCount = 0
    private val maxAttempts = 3
    private val DEEPFAKE_REQUEST_CODE = 1001

    private lateinit var supabaseService : SupabaseService
    private lateinit var supabaseClient: SupabaseClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        textureView = findViewById(R.id.camera)
        capturedImage = findViewById(R.id.imageView)
        capturedButton = findViewById(R.id.captureButton)
        retakeButton = findViewById(R.id.retake)
        confirmButton = findViewById(R.id.confirm)

        cameraExecutor = Executors.newSingleThreadExecutor()

        supabaseClient = subase
        supabaseService = SupabaseService(supabaseClient)

        fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
            return try {
                // Remove extra spaces or newlines
                val cleanBase64 = base64Str.trim()

                // Decode Base64 String into ByteArray
                val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)

                // Convert ByteArray to Bitmap
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        }else{
            requestPermission.launch(REQUIRED_PERMISSIONS)
        }
        capturedButton.setOnClickListener { capturePhoto() }
        retakeButton.setOnClickListener { retakePhoto() }
        confirmButton.setOnClickListener {
            val imageBytes = convertImagetoByteArray()
            if (imageBytes == null) {
                Toast.makeText(this, "Failed to Capture Image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val caputuredbase64img = Base64.encodeToString(imageBytes,Base64.DEFAULT)

//            Log.d("CapturedImage", "Captured Image (Base64): $caputuredbase64img")
            Toast.makeText(this, "Image Captured and Converted", Toast.LENGTH_SHORT).show()



            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userEmail = intent.getStringExtra("email")
                    if (userEmail.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CameraActivity, "User email not found", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val facenetModel = supabaseService.loadFaceNetModel("mobilefacenet.tflite", this@CameraActivity)
                    if (facenetModel == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CameraActivity, "Failed to load FaceNet model", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val storedImageBytes = withContext(Dispatchers.IO) {
                        withTimeoutOrNull(5000) { // 5 seconds timeout
                            suspendCoroutine<ByteArray?> { continuation ->
                                supabaseService.fetchStoredImageBytes(userEmail) { result ->
                                    continuation.resume(result)
                                }
                            }
                        }
                    }

                    if (storedImageBytes == null) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CameraActivity, "No stored image found or request timed out!", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val decodeBase64 = String(storedImageBytes)
                    val storedBase64 =Base64.encodeToString(Base64.decode(decodeBase64,Base64.DEFAULT),Base64.DEFAULT)

                    Log.d("ImageFetch", "Fetched Image (Base64): $storedBase64")
                    val storedBitmap = decodeBase64ToBitmap(storedBase64)
                    if (storedBitmap != null) {
                        Log.d("ImageSuccess", "Stored image decoded successfully")
                    } else {
                        Log.e("ImageError", "Failed to decode Stored Base64 image")
                    }

                    val capturedBitmap = decodeBase64ToBitmap(caputuredbase64img)
                    if (capturedBitmap != null) {
                        Log.d("ImageSuccess","Decoded Successfully")
                    } else {
                        Log.e("ImageError", "Failed to decode Base64 image")
                    }


                    val capturedProcessed = supabaseService.preprocessFace(capturedBitmap!!)
                    val storedProcessed = supabaseService.preprocessFace(storedBitmap!!)

                    val capturedEmbedding = supabaseService.extractFaceNetEmbeddings(capturedProcessed, facenetModel)
                    val storedEmbedding = supabaseService.extractFaceNetEmbeddings(storedProcessed, facenetModel)

                    // Compute Similarity
                    val similarity = supabaseService.calculateCosineSimilarity(capturedEmbedding, storedEmbedding)
                    val distance = supabaseService.calculateEuclideanDistance(capturedEmbedding, storedEmbedding)

                    val maxPossibleDistance = sqrt(capturedEmbedding.size.toFloat())
                    val normalizedDistance = 1 - (distance / maxPossibleDistance)

                    val finalMatchScore = (0.7 * similarity) + (0.3 * normalizedDistance)
                    val matchPercentage = (finalMatchScore * 100).toInt().coerceIn(0, 100)
                    val isMatch = supabaseService.isFaceMatch(capturedEmbedding, storedEmbedding)

                    withContext(Dispatchers.Main) {
                        if (matchPercentage >= 70) {

                            Toast.makeText(this@CameraActivity, "✅ Face Matched! ($matchPercentage%)", Toast.LENGTH_LONG).show()
                            val intent = Intent(this@CameraActivity, VerificationActivity::class.java)
                            intent.putExtra("isProcessing", true)
                            // Just to indicate loading
                            startActivity(intent)

// Run deepfake detection in the background
                            CoroutineScope(Dispatchers.IO).launch {
                                val deepfakeModel = supabaseService.loadDeepfakeModel("deepfake_detector.tflite", this@CameraActivity)
                                val deepfakeResult = supabaseService.runDeepfakeModel(deepfakeModel!!, capturedBitmap)
                                Log.d("DeepfakeModel", "Captured image is being sent for verification.")
                                withContext(Dispatchers.Main) {
                                    // Send result back to VerificationActivity
                                    val resultIntent = Intent("com.example.DEEPFAKE_RESULT")
                                    resultIntent.putExtra("deepfake_result", deepfakeResult)
                                    LocalBroadcastManager.getInstance(this@CameraActivity).sendBroadcast(resultIntent)
                                }
                            }
                        } else {
                            attemptCount++
                            if (attemptCount < maxAttempts) {
                                Toast.makeText(this@CameraActivity, "❌ Not Matched ($matchPercentage%). Attempts left: ${maxAttempts - attemptCount}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@CameraActivity, "Authentication failed", Toast.LENGTH_LONG).show()
                                CoroutineScope(Dispatchers.IO).launch {
                                    sendSuspiciousActivityEmail(userEmail)
                                }
                                startActivity(Intent(this@CameraActivity, MainActivity::class.java))
                                finish()
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("FaceNet", "Error in face comparison: ${e.message}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CameraActivity, "An error occurred: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


    }


    suspend fun sendSuspiciousActivityEmail(userEmail: String) {
        withContext(Dispatchers.IO) { // Ensure execution in background thread
            try {
                val apiKey = BuildConfig.SENDGRID_API_KEY
                Log.d("SendGrid", "API Key: $apiKey")
                val url = "https://api.sendgrid.com/v3/mail/send"

                val requestBody = JSONObject().apply {
                    put("personalizations", JSONArray().apply {
                        put(JSONObject().apply {
                            put("to", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("email", userEmail)
                                })
                            })
                            put("subject", "⚠ Suspicious Activity Detected on Your Account\n")
                        })
                    })
                    put("from", JSONObject().apply {
                        put("email", "ahiraomrudul@gmail.com")
                    })
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text/plain")
                            put("value", "Dear User,\n" +
                                    "\n" +
                                    "We have detected unusual activity on your DigiRakshaX account. To ensure your security, we recommend verifying your login attempt immediately.\n" +
                                    "\n" +
                                    "If this was you, no further action is needed. However, if you suspect unauthorized access, please reset your password and enable additional security measures.\n" +
                                    "\n" +
                                    "Your safety is our priority. Stay secure with DigiRakshaX.\n" +
                                    "\n" +
                                    "Best regards,\n" +
                                    "DigiRakshaX Security Team")
                        })
                    })
                }

                val request = requestBody.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())

                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(request)
                    .build()

                val client = OkHttpClient.Builder().build()
                val response = client.newCall(req).execute()

                if (response.isSuccessful) {
                    Log.d("SendGrid", "Email sent successfully")
                } else {
                    Log.e("SendGrid", "Failed to send email: ${response.code} - ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("SendGrid", "Error sending email: ${e.localizedMessage}")
            }
        }
    }


    private fun convertImagetoByteArray(): ByteArray?{
        val bitmap = textureView.bitmap ?: return null

        return bitmap?.let{
            val stream = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG,100,stream)
            stream.toByteArray()
        }
    }
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){
            permissions ->
        if (permissions.values.all{it}) {
            startCamera()
        }else{
            finish()
        }
    }
    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetResolution(Size(1280,720))
                .build()
                .also {
                    it.setSurfaceProvider { request ->
                        val surface = Surface(textureView.surfaceTexture)
                        request.provideSurface(surface, cameraExecutor){}
                    }
                }
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1280,720))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            }catch (e: Exception){
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun capturePhoto() {
        imageCapture?.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = textureView.bitmap?.let { rotateBitmap(it,360f) }
                capturedImage.setImageBitmap(bitmap)
                capturedImage.visibility = ImageView.VISIBLE
                textureView.visibility = TextureView.GONE

                capturedButton.visibility = ImageButton.GONE
                retakeButton.visibility = MaterialButton.VISIBLE
                confirmButton.visibility = MaterialButton.VISIBLE

                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        })
    }
    private fun retakePhoto() {
        capturedImage.visibility = ImageView.GONE
        textureView.visibility = TextureView.VISIBLE

        capturedButton.visibility = ImageButton.VISIBLE
        retakeButton.visibility = MaterialButton.GONE
        confirmButton.visibility = MaterialButton.GONE
    }

    private fun rotateBitmap(bitmap: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}