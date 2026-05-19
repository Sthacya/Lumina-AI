package com.example.safetydriveai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.safetydriveai.databinding.ActivityLandingBinding

class ActivityLanding : AppCompatActivity() {
    private lateinit var binding: ActivityLandingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartSystem.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}