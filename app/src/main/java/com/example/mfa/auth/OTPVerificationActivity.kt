package com.example.mfa.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mfa.R
import com.example.mfa.services.SupabaseService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


val sbase = createSupabaseClient(
    supabaseUrl = "https://jisunuvrdgpwxemwkdrh.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imppc3VudXZyZGdwd3hlbXdrZHJoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzg4NjUwNDAsImV4cCI6MjA1NDQ0MTA0MH0.W_xSAkPwA4O1alTI_YQqHQoNwUQWb3-VxkewR6Qc4GY"
) {
    install(Postgrest)
    install(Auth)
}

class OTPVerificationActivity : AppCompatActivity() {

    private lateinit var otpFields: Array<EditText>
    private lateinit var btnSubmitOtp: MaterialButton
    private lateinit var tvResendOtp: MaterialTextView
    private lateinit var progressBar: ProgressBar
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var supabaseService: SupabaseService
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otpverification)

        // Initialize SupabaseService
        supabaseClient = sbase
        supabaseService = SupabaseService(supabaseClient)

        // Get email from intent
        email = intent.getStringExtra("email") ?: ""
        if (email.isEmpty()) {
            Toast.makeText(this, "Email not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupOTPFieldListeners()
        sendInitialOTP()
    }

    private fun initializeViews() {
        otpFields = arrayOf(
            findViewById(R.id.otpDigit1),
            findViewById(R.id.otpDigit2),
            findViewById(R.id.otpDigit3),
            findViewById(R.id.otpDigit4),
            findViewById(R.id.otpDigit5),
            findViewById(R.id.otpDigit6)
        )

        btnSubmitOtp = findViewById(R.id.btnSubmitOtp)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        progressBar = findViewById(R.id.progressBar)

        btnSubmitOtp.setOnClickListener { verifyOTP() }
        tvResendOtp.setOnClickListener { resendOTP() }
    }

    private fun setupOTPFieldListeners() {
        for (i in 0 until otpFields.size - 1) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        otpFields[i + 1].requestFocus()
                    } else if (s?.isEmpty() == true && i > 0) {
                        otpFields[i - 1].requestFocus()
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            otpFields[i].setOnKeyListener { _, keyCode, _ ->
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL && otpFields[i].text.isEmpty() && i > 0) {
                    otpFields[i - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun sendInitialOTP() {
        btnSubmitOtp.isEnabled = false
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = supabaseService.sendEmailOTP(email)
            runOnUiThread {
                btnSubmitOtp.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(this@OTPVerificationActivity,
                        "OTP sent to $email", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@OTPVerificationActivity,
                        "Failed to send OTP. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun verifyOTP() {
        val enteredOTP = otpFields.joinToString("") { it.text.toString().trim() }

        if (enteredOTP.length != 6) {
            Toast.makeText(this, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmitOtp.isEnabled = false
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = supabaseService.verifyEmailOTP(email, enteredOTP)
            runOnUiThread {
                btnSubmitOtp.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(this@OTPVerificationActivity,
                        "OTP Verified Successfully!", Toast.LENGTH_SHORT).show()
                    // TODO: Navigate to your next screen
                    val intent = Intent(this@OTPVerificationActivity, CameraActivity::class.java)
                    intent.putExtra("email",email)
                    startActivity(intent)
                     finish()
                } else {
                    Toast.makeText(this@OTPVerificationActivity,
                        "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
                    // Clear OTP fields on failure
                    otpFields.forEach { it.text.clear() }
                    otpFields[0].requestFocus()
                }
            }
        }
    }

    private fun resendOTP() {
        tvResendOtp.isEnabled = false
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val success = supabaseService.sendEmailOTP(email)
            runOnUiThread {
                tvResendOtp.isEnabled = true
                progressBar.visibility = View.GONE
                if (success) {
                    Toast.makeText(this@OTPVerificationActivity,
                        "New OTP sent to $email", Toast.LENGTH_SHORT).show()
                    otpFields.forEach { it.text.clear() }
                    otpFields[0].requestFocus()
                } else {
                    Toast.makeText(this@OTPVerificationActivity,
                        "Failed to resend OTP. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
