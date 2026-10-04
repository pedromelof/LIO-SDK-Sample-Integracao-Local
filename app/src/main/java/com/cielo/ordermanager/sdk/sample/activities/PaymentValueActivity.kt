package com.cielo.ordermanager.sdk.sample.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.cielo.ordermanager.sdk.R
import com.cielo.ordermanager.sdk.databinding.ActivityPaymentValueBinding
import com.cielo.ordermanager.sdk.utils.DeepLinkPaymentHelper
import com.cielo.ordermanager.sdk.utils.Item
import com.cielo.ordermanager.sdk.utils.OrderRequest
import com.cielo.ordermanager.sdk.utils.getBase64
import com.cielo.ordermanager.sdk.utils.startForegroundServiceAndLaunchDeepLink
import com.google.gson.Gson
import java.text.NumberFormat
import java.util.Locale

/**
 * Tela de digitação de valor.
 *
 * IDs de layout assumidos (ajuste caso seu XML use nomes diferentes):
 *   tv_amount, btn_0..btn_9,
 *   btn_credito_avista, btn_credito_parcelado, btn_debito, btn_pix,
 *   btn_calculator
 *
 * Regra de negócio: o valor é sempre mantido em CENTAVOS (Long).
 * A cada dígito novo, "empurramos" o valor uma casa para a esquerda
 * (multiplica por 10) e somamos o dígito — isso naturalmente preenche
 * da direita para a esquerda, como um caixa eletrônico:
 *   0 -> digita 1 -> 1        (R$ 0,01)
 *   1 -> digita 0 -> 10       (R$ 0,10)
 *   10 -> digita 5 -> 105     (R$ 1,05)
 *   105 -> digita 0 -> 1050   (R$ 10,50)
 */
class PaymentValueActivity : AppCompatActivity() {


    private val scheme by lazy { getString(R.string.intent_scheme) }
    private val responseHost by lazy { getString(R.string.intent_host) }
    private val callbackUrl by lazy { "$scheme://$responseHost" }
    private val callbackUrlSameActivity by lazy { "${getString(R.string.intent_scheme_same_activity)}://${getString(
        R.string.intent_host)}" }
    private val reference get() = "uriapp #" + (System.currentTimeMillis() / 1000)


    private lateinit var binding: ActivityPaymentValueBinding

    // Valor atual em centavos. Fonte única de verdade para o valor digitado.
    private var amountInCents: Long = 0L

    // Limite de dígitos para não estourar valores absurdos por toque acidental
    // (ex: R$ 999.999.999,99 = 11 dígitos). Ajuste conforme a regra de negócio.
    private val maxDigits = 11

    private val currencyFormat: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    /**
     * Activity Result API: substitui o startActivityForResult/onActivityResult
     * obsoletos. Recebe de volta o valor calculado na PaymentCalculatorActivity.
     */
    private val calculatorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val calculatedCents = result.data?.getLongExtra(
                PaymentCalculatorActivity.EXTRA_RESULT_AMOUNT_CENTS, -1L
            ) ?: -1L

            if (calculatedCents >= 0) {
                amountInCents = calculatedCents.coerceAtMost(maxValueForDigits())
                updateAmountDisplay()
            }
        }
        // Se resultCode != RESULT_OK (ex: usuário voltou sem calcular), não fazemos nada:
        // o valor digitado anteriormente na tela permanece intacto.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentValueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateAmountDisplay()
        setupNumericPad()
        setupPaymentButtons()
    }

    // region Teclado numérico

    private fun setupNumericPad() {
        val digitButtons = listOf(
            binding.btn0 to 0, binding.btn1 to 1, binding.btn2 to 2,
            binding.btn3 to 3, binding.btn4 to 4, binding.btn5 to 5,
            binding.btn6 to 6, binding.btn7 to 7, binding.btn8 to 8,
            binding.btn9 to 9
        )

        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { onDigitPressed(digit) }
        }
    }

    private fun onDigitPressed(digit: Int) {
        if (String.format("%d", amountInCents).length >= maxDigits) {
            // Evita overflow silencioso e feedback confuso ao operador.
            return
        }
        amountInCents = amountInCents * 10 + digit
        updateAmountDisplay()
    }

    private fun maxValueForDigits(): Long {
        var value = 1L
        repeat(maxDigits) { value *= 10 }
        return value - 1
    }

    private fun updateAmountDisplay() {
        val reais = amountInCents / 100.0
        binding.tvAmount.text = currencyFormat.format(reais)
    }

    // endregion

    // region Botões de pagamento

    private fun setupPaymentButtons() {
        binding.btnCreditoAvista.setOnClickListener {
            startPaymentIfValid(PaymentType.CREDITO_AVISTA)
        }

        binding.btnCreditoParcelado.setOnClickListener {
            // Fluxo de parcelamento tem tela própria para escolher o número de parcelas.
            startActivity(Intent(this, InstallmentsActivity::class.java))
        }

        binding.btnDebito.setOnClickListener {
            startPaymentIfValid(PaymentType.DEBITO_AVISTA)
        }

        binding.btnPix.setOnClickListener {
            startPaymentIfValid(PaymentType.PIX)
        }

        binding.btnCalculator.setOnClickListener {
            calculatorLauncher.launch(Intent(this, PaymentCalculatorActivity::class.java))
        }
    }

    private fun startPaymentIfValid(paymentType: PaymentType) {
        if (amountInCents <= 0) {
            binding.tvAmount.error = null // no-op seguro caso tv_amount não suporte error
            android.widget.Toast.makeText(
                this, "Digite um valor antes de continuar.", android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        makePayment(amountInCents, paymentType)
    }

    /**
     * Refatoração do trecho original: nenhum valor de negócio (valor ou tipo)
     * é mais hardcoded — ambos vêm da tela. A montagem do OrderRequest, o
     * JSON, o Base64 e o disparo do Deep Link ficam encapsulados em
     * DeepLinkPaymentHelper, mantendo esta Activity focada em UI.
     */
    private fun makePayment(valorFinal: Long, tipoDePagamento: PaymentType) {

        val price = valorFinal
        val quantity = (1..5).random()
        val randomSku: Int = (1000..100000).random()
        val item = Item(
            sku = randomSku.toString(),
            name = "Produto de Teste",
            unitPrice = price,
            quantity = quantity,
            unitOfMeasure = "unidade"
        )
        val items = mutableListOf(item)

        // ["CREDITO_AVISTA","CREDITO_PARCELADO_BNCO","CREDITO_PARCELADO_ADM","CREDITO_PARCELADO_LOJA","DEBITO_PAGTO_FATURA_DEBITO","DEBITO_AVISTA","VOUCHER_REFEICAO","VOUCHER_ALIMENTACAO","VOUCHER_AUTOMOTIVO","VOUCHER_CULTURA","VOUCHER_PEDAGIO","VOUCHER_BENEFICIOS","VOUCHER_AUTO","VOUCHER_CONSULTA_SALDO","VOUCHER_VALE_PEDAGIO","CARTAO_LOJA_AVISTA","CARTAO_LOJA_PARCELADO_LOJA","CARTAO_LOJA_PARCELADO_BANCO","CARTAO_LOJA_PAGTO_FATURA_CHEQUE","CARTAO_LOJA_PAGTO_FATURA_DINHEIRO","PRE_AUTORIZACAO","PIX"]
        val request = OrderRequest(
            "xxxxxxxxxxxxxxxxx",
            "xxxxxxxxxxxxxxxxxxxxxx",
            price,
            tipoDePagamento.toString(),
            1,
            "eduardo.vianna@m4u.com.br",
            null,
            reference,
            items,
        )

        val json = Gson().toJson(request).toString()
        val base64 = getBase64(json)
        val checkoutUri = "lio://payment?request=$base64&urlCallback=$callbackUrl"
        startForegroundServiceAndLaunchDeepLink(this, checkoutUri)
    }

    // endregion
}