package com.cielo.ordermanager.sdk.sample.activities

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import com.cielo.ordermanager.sdk.R
import com.cielo.ordermanager.sdk.databinding.ActivityActionsBinding
import com.cielo.ordermanager.sdk.utils.PrintRequest
import com.cielo.ordermanager.sdk.utils.getBase64
import com.cielo.ordermanager.sdk.utils.saveImage
import com.cielo.ordermanager.sdk.utils.startForegroundServiceAndLaunchDeepLink
import com.google.gson.Gson

/**
 * Tela inicial do PDV. Único responsável: iniciar o fluxo de cobrança.
 */
class ActionsActivity : AppCompatActivity() {

    private val scheme by lazy { getString(R.string.intent_scheme) }
    private val responseHost by lazy { getString(R.string.intent_host) }
    private val callbackUrl by lazy { "$scheme://$responseHost" }
    private val callbackUrlSameActivity by lazy { "${getString(R.string.intent_scheme_same_activity)}://${getString(
        R.string.intent_host)}" }

    private lateinit var binding: ActivityActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.paymentButton.setOnClickListener {
            startActivity(Intent(this, PaymentValueActivity::class.java))
        }
    }
}