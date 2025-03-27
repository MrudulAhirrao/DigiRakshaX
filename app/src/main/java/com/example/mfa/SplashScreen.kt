package com.example.mfa

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.animation.AnimationUtils

class SplashScreen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        val logo = findViewById<ImageView>(R.id.logo)
        val appName = findViewById<TextView>(R.id.App_name)

        val logoAnimation = android.view.animation.AnimationUtils.loadAnimation(this,R.anim.logo_animation)
        val textAnimation = android.view.animation.AnimationUtils.loadAnimation(this,R.anim.text_animation)

        logo.startAnimation(logoAnimation)
        appName.startAnimation(textAnimation)

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        },2500)
    }
}