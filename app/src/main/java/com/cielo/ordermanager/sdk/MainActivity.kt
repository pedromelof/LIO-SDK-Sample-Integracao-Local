package com.cielo.ordermanager.sdk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cielo.ordermanager.sdk.databinding.ActivityActionsBinding
import com.cielo.ordermanager.sdk.databinding.ActivityMainBinding
import com.cielo.ordermanager.sdk.sample.activities.DeepLinkIntegrationActivity
import com.cielo.ordermanager.sdk.sample.activities.LocalIntegrationActivity
import com.cielo.ordermanager.sdk.sample.activities.PrintSampleActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.paymentButton.setOnClickListener {
            startActivity(Intent(this, DeepLinkIntegrationActivity::class.java))
        }
    }
}