package com.cielo.ordermanager.sdk.sample.activities

/**
 * Tipos de pagamento suportados pelo PDV.
 * Usar um enum evita "strings mágicas" espalhadas pelo código.
 */
enum class PaymentType {
    CREDITO_AVISTA,
    CREDITO_PARCELADO,
    DEBITO_AVISTA,
    PIX
}

/**
 * Item de uma venda. unitPrice é sempre em CENTAVOS (Long) para evitar
 * problemas de arredondamento com Float/Double.
 */
data class Item(
    val sku: String,
    val name: String,
    val unitPrice: Long,
    val quantity: Int,
    val unitOfMeasure: String
)

/**
 * Payload enviado via Deep Link para o app de pagamento (lio://payment).
 *
 * Observação: os campos abaixo foram nomeados com base na ordem posicional
 * do trecho original fornecido. Ajuste os nomes/tipos conforme o contrato
 * real da API de pagamentos, se ele divergir do que foi inferido aqui.
 */
data class OrderRequest(
    val merchantId: String,
    val terminalId: String,
    val amount: Long,
    val discount: Long?,
    val installments: Int,
    val customerEmail: String,
    val customerName: String?,
    val reference: String,
    val items: List<Item>
)