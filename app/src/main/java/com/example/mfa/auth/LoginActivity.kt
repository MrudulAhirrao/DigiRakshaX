package com.example.mfa.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.mfa.R
import com.example.mfa.services.SupabaseService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val sb = createSupabaseClient(
    supabaseUrl = "https://jisunuvrdgpwxemwkdrh.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imppc3VudXZyZGdwd3hlbXdrZHJoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg4NjUwNDAsImV4cCI6MjA1NDQ0MTA0MH0.W_xSAkPwA4O1alTI_YQqHQoNwUQWb3-VxkewR6Qc4GY"
) {
    install(Postgrest)
    install(Auth)
}
class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button


    private lateinit var supabaseService: SupabaseService
    private lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Initialize UI components
        emailInput = findViewById(R.id.user)
        passwordInput = findViewById(R.id.pass)
        loginButton = findViewById(R.id.button)

        // Initialize Supabase
        supabaseClient = sb
        supabaseService = SupabaseService(supabaseClient)

        // Set click listener for login
        loginButton.setOnClickListener {
            loginUser()
        }
    }

    private fun loginUser() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress bar and disable button
        loginButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val success = supabaseService.loginUser(email, password)

            runOnUiThread {
                loginButton.isEnabled = true

                if (success) {
                    Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()

                    // Navigate to OTP Verification (You can replace this with your OTP Activity)
                    val intent = Intent(this@LoginActivity, OTPVerificationActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Login Failed. Check your credentials.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
