package com.example.mfa.auth

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mfa.R
import com.example.mfa.services.SupabaseService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

val supabase = createSupabaseClient(
    supabaseUrl = "https://jisunuvrdgpwxemwkdrh.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imppc3VudXZyZGdwd3hlbXdrZHJoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg4NjUwNDAsImV4cCI6MjA1NDQ0MTA0MH0.W_xSAkPwA4O1alTI_YQqHQoNwUQWb3-VxkewR6Qc4GY"
) {
    install(Postgrest)
    install(Auth)
}

class RegistrationActivity : AppCompatActivity() {
    private lateinit var supabaseService: SupabaseService
    private lateinit var supabaseClient: SupabaseClient

    // UI Elements
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText

    private var capturedImageBytes: ByteArray? = null

    // Request code for camera permission
    private val cameraPermissionRequestCode = 1001

    // Launch activity for capturing image
    private val imageCaptureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as? Bitmap
                if (imageBitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    capturedImageBytes = outputStream.toByteArray()
                    Toast.makeText(this, "Image Captured!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        // Initialize Supabase Client and Service
        supabaseClient = supabase
        supabaseService = SupabaseService(supabaseClient)

        // Check if camera permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), cameraPermissionRequestCode)
        }

        // Bind UI elements
        nameInput = findViewById(R.id.name)
        emailInput = findViewById(R.id.email)
        passwordInput = findViewById(R.id.pass)
        confirmPasswordInput = findViewById(R.id.cpass)

        // Set click listeners
        findViewById<MaterialButton>(R.id.capture_upload_button).setOnClickListener {
            captureOrUploadImage()
        }

        findViewById<MaterialButton>(R.id.button).setOnClickListener {
            registerUser()
        }
    }

    // Method to handle camera permission result
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == cameraPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Method to handle the image capture
    private fun captureOrUploadImage() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        imageCaptureLauncher.launch(takePictureIntent)
    }

    // Method to register the user
    private fun registerUser() {
        val fullName = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        // Validate input
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Register user in a coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val isSuccess = supabaseService.registerUser(fullName, email, password, capturedImageBytes)
            runOnUiThread {
                if (isSuccess) {
                    Toast.makeText(
                        this@RegistrationActivity,
                        "Registration Successful!",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@RegistrationActivity, LoginActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(
                        this@RegistrationActivity,
                        "Registration Failed!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    }
}
