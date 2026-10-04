package com.cielo.ordermanager.sdk.sample.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cielo.ordermanager.sdk.databinding.ActivityInstallmentsBinding

/**
 * Tela de parcelamento (esqueleto).
 *
 * IDs de layout assumidos: btn_back.
 *
 * TODO: implementar a listagem/seleção do número de parcelas e, ao
 * confirmar, chamar DeepLinkPaymentHelper.makePayment(...) com
 * tipoDePagamento = PaymentType.CREDITO_PARCELADO e o número de parcelas
 * escolhido (parâmetro `installments`).
 */
class InstallmentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInstallmentsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallmentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConfirmInstallments.setOnClickListener {
            finish()
        }
    }
}