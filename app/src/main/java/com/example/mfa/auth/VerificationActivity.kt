package com.example.mfa.auth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.airbnb.lottie.LottieAnimationView
import com.example.mfa.MainActivity
import com.example.mfa.R
import com.example.mfa.services.SupabaseService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.util.logging.Handler


class VerificationActivity : AppCompatActivity() {
    private lateinit var resultAnimation: LottieAnimationView

    private lateinit var supabaseService : SupabaseService
    private lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_verification)
        resultAnimation = findViewById(R.id.resultAnimation)

        supabaseClient = subase
        supabaseService = SupabaseService(supabaseClient)
        // Register BroadcastReceiver to listen for deepfake result
        val filter = IntentFilter("com.example.DEEPFAKE_RESULT")
        LocalBroadcastManager.getInstance(this).registerReceiver(deepfakeResultReceiver, filter)

    }
    private val deepfakeResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val deepfakeDetected = intent?.getFloatExtra("deepfake_result", 0.0f) ?: 0.0f


            // Update UI based on result
            if (deepfakeDetected>=50) {
                resultAnimation.setAnimation(R.raw.failed_animation)  // Use failure Lottie animation
                resultAnimation.visibility = View.VISIBLE
                resultAnimation.playAnimation()

                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@VerificationActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }, 2000)
            } else {
                resultAnimation.setAnimation(R.raw.success_animation) // Use success Lottie
                resultAnimation.visibility = View.VISIBLE
                resultAnimation.repeatCount = LottieAnimationView.VISIBLE // Keep running
                resultAnimation.playAnimation()
            }

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deepfakeResultReceiver)
    }
}