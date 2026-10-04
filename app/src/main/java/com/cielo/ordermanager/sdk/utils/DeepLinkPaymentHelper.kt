package com.cielo.ordermanager.sdk.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.cielo.ordermanager.sdk.R
import com.cielo.ordermanager.sdk.sample.activities.PaymentType
import com.google.gson.Gson

/**
 * Centraliza a lógica de:
 *  1) Montar o OrderRequest de forma dinâmica (sem valores hardcoded de valor/tipo).
 *  2) Serializar para JSON (Gson).
 *  3) Codificar em Base64.
 *  4) Disparar o Deep Link "lio://payment" para o app de pagamento.
 *
 * Mantido como um objeto separado (em vez de dentro da Activity) para que a
 * lógica de pagamento fique testável e reaproveitável por qualquer tela que
 * precise iniciar um pagamento (ex: PaymentValueActivity).
 */
object DeepLinkPaymentHelper {



    private const val TAG = "DeepLinkPaymentHelper"
    private const val DEEP_LINK_SCHEME = "lio://payment"

    // TODO: mover para BuildConfig / configuração remota antes de ir para produção.
    // Estes NÃO são o "valor" ou o "tipo de pagamento" (que são dinâmicos) — são
    // credenciais fixas do estabelecimento/terminal, por isso permanecem como
    // constantes de configuração.
    private const val MERCHANT_ID = "xxxxxxxxxxxxxxxxx"
    private const val TERMINAL_ID = "xxxxxxxxxxxxxxxxxxxxxx"
    private const val CUSTOMER_EMAIL = "email@exemplo.com.br"

    /**
     * Monta o pedido, converte para Base64 e inicia o Deep Link.
     *
     * @param context contexto usado para iniciar a Intent e (se aplicável) o serviço.
     * @param valorFinalCentavos valor total da cobrança, em centavos.
     * @param tipoDePagamento tipo de pagamento escolhido pelo usuário.
     * @param installments número de parcelas (1 para à vista).
     * @param reference referência única da transação (ex: UUID ou timestamp).
     */
    fun makePayment(
        context: Context,
        valorFinalCentavos: Long,
        tipoDePagamento: PaymentType,
        installments: Int = 1,
        reference: String = generateReference()
    ) {
        if (valorFinalCentavos <= 0) {
            Toast.makeText(context, "Informe um valor válido antes de continuar.", Toast.LENGTH_SHORT).show()
            return
        }

        val item = Item(
            sku = "12345",
            name = "Pagamento Avulso",
            unitPrice = valorFinalCentavos,
            quantity = 1,
            unitOfMeasure = "unidade"
        )

        val request = OrderRequest(
            "xxxxxxxxxxxxxxxxx",
            "xxxxxxxxxxxxxxxxxxxxxx",
            valorFinalCentavos,
            null,
            1,
            "eduardo.vianna@m4u.com.br",
            null,
            reference,
            items = listOf(item).toMutableList()
        )

        try {
            val json = Gson().toJson(request)
            val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            startForegroundServiceAndLaunchDeepLink(context, encoded, tipoDePagamento)
        } catch (e: Exception) {
            // Nunca deixe uma falha de serialização derrubar o app do operador de caixa.
            Log.e(TAG, "Falha ao montar/enviar o pagamento", e)
            Toast.makeText(context, "Não foi possível iniciar o pagamento. Tente novamente.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Dispara o Deep Link para o app de pagamento. O tipo de pagamento é enviado
     * como parâmetro de query para que o app receptor saiba qual fluxo abrir
     * (crédito, débito, pix, etc).
     *
     * Se o seu fluxo real exigir um Foreground Service para acompanhar o
     * status do pagamento (ex: notificação persistente "Aguardando pagamento..."),
     * inicie-o aqui antes de lançar a Intent — o esqueleto está comentado abaixo.
     */
    private fun startForegroundServiceAndLaunchDeepLink(
        context: Context,
        encodedPayload: String,
        tipoDePagamento: PaymentType
    ) {
        val uri = Uri.parse(DEEP_LINK_SCHEME)
            .buildUpon()
            .appendQueryParameter("data", encodedPayload)
            .appendQueryParameter("paymentType", tipoDePagamento.name)
            .build()

        // Exemplo de onde entraria um Foreground Service, caso necessário:
        // val serviceIntent = Intent(context, PaymentStatusService::class.java).apply {
        //     putExtra(PaymentStatusService.EXTRA_REFERENCE, reference)
        // }
        // ContextCompat.startForegroundService(context, serviceIntent)

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { context !is android.app.Activity } ?: 0)
        }

        try {
            context.startActivity(deepLinkIntent)
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "Nenhum app instalado responde por $DEEP_LINK_SCHEME", e)
            Toast.makeText(context, "App de pagamento não encontrado no dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateReference(): String = "ref_${System.currentTimeMillis()}"
}