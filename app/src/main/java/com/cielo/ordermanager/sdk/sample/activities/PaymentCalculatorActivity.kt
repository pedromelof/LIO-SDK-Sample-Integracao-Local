package com.cielo.ordermanager.sdk.sample.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cielo.ordermanager.sdk.databinding.ActivityPaymentCalculatorBinding
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculadora financeira simples.
 *
 * IDs de layout assumidos:
 *   tv_display, btn_0..btn_9, btn_dot,
 *   btn_add, btn_subtract, btn_multiply, btn_divide, btn_percent, btn_clear,
 *   btn_go_to_payment, btn_back
 *
 * Estado gerenciado com 3 variáveis clássicas de calculadora:
 *   - previousOperand: valor já "fechado" antes da operação pendente
 *   - pendingOperation: operação escolhida (+, -, *, /, %) aguardando o 2º operando
 *   - currentInput: texto sendo digitado no visor (o operando atual)
 *
 * Todos os cálculos usam BigDecimal para evitar erros de ponto flutuante
 * (ex: 0.1 + 0.2 != 0.3 em Double) e o resultado final é sempre arredondado
 * para 2 casas decimais.
 */
class PaymentCalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentCalculatorBinding

    private var previousOperand: BigDecimal? = null
    private var pendingOperation: Char? = null
    private var currentInput: String = "0"

    // Depois de um "=" implícito (ao trocar de operação) ou de um erro,
    // o próximo dígito digitado deve começar um número novo, não concatenar.
    private var shouldResetInput: Boolean = true
    private var isInErrorState: Boolean = false

    companion object {
        const val EXTRA_RESULT_AMOUNT_CENTS = "extra_result_amount_cents"
        private const val MAX_INTEGER_DIGITS = 9
        private const val MAX_DECIMAL_DIGITS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateDisplay()
        setupNumericPad()
        setupOperationButtons()
        setupNavigationButtons()
    }

    // region Setup

    private fun setupNumericPad() {
        val digitButtons = listOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9"
        )
        digitButtons.forEach { (button, digit) ->
            button.setOnClickListener { onDigitPressed(digit) }
        }
        binding.btnDot.setOnClickListener { onDotPressed() }
    }

    private fun setupOperationButtons() {
        binding.btnAdd.setOnClickListener { onOperationPressed('+') }
        binding.btnSubtract.setOnClickListener { onOperationPressed('-') }
        binding.btnMultiply.setOnClickListener { onOperationPressed('*') }
        binding.btnDivide.setOnClickListener { onOperationPressed('/') }
        binding.btnPercent.setOnClickListener { onPercentPressed() }
        binding.btnClear.setOnClickListener { onClearPressed() }
    }

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPagamento.setOnClickListener { onGoToPaymentPressed() }
    }

    // endregion

    // region Entrada de dígitos

    private fun onDigitPressed(digit: String) {
        if (isInErrorState) {
            onClearPressed()
        }

        if (shouldResetInput) {
            currentInput = digit
            shouldResetInput = false
        } else {
            val (integerPart, decimalPart) = splitInput(currentInput)
            val isAtIntegerLimit = decimalPart == null && integerPart.length >= MAX_INTEGER_DIGITS
            val isAtDecimalLimit = decimalPart != null && decimalPart.length >= MAX_DECIMAL_DIGITS

            if (isAtIntegerLimit || isAtDecimalLimit) return

            currentInput = if (currentInput == "0") digit else currentInput + digit
        }
        updateDisplay()
    }

    private fun onDotPressed() {
        if (isInErrorState) onClearPressed()

        if (shouldResetInput) {
            currentInput = "0."
            shouldResetInput = false
        } else if (!currentInput.contains(".")) {
            currentInput += "."
        }
        updateDisplay()
    }

    private fun splitInput(input: String): Pair<String, String?> {
        val parts = input.split(".")
        return parts[0] to parts.getOrNull(1)
    }

    // endregion

    // region Operações

    private fun onOperationPressed(operation: Char) {
        if (isInErrorState) return

        val current = currentInput.toBigDecimalOrNull() ?: return

        if (previousOperand != null && pendingOperation != null && !shouldResetInput) {
            // Já existe uma operação pendente e um novo operando foi digitado:
            // resolvemos em cadeia (ex: 5 + 3 + 2 -> calcula 5+3 antes de guardar o +2).
            val result = calculate(previousOperand!!, current, pendingOperation!!) ?: return
            previousOperand = result
            currentInput = formatBigDecimal(result)
        } else {
            previousOperand = current
        }

        pendingOperation = operation
        shouldResetInput = true
        updateDisplay()
    }

    private fun onPercentPressed() {
        if (isInErrorState) return
        val current = currentInput.toBigDecimalOrNull() ?: return

        val result = if (previousOperand != null) {
            // Porcentagem relativa ao valor anterior, ex: 200 + 10% => 200 + 20
            previousOperand!!.multiply(current).divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        } else {
            current.divide(BigDecimal(100), 10, RoundingMode.HALF_UP)
        }

        currentInput = formatBigDecimal(result)
        shouldResetInput = true
        updateDisplay()
    }

    /**
     * Executa a operação pendente. Trata divisão por zero de forma segura,
     * exibindo "Erro" no visor em vez de derrubar o app.
     */
    private fun calculate(a: BigDecimal, b: BigDecimal, operation: Char): BigDecimal? {
        return try {
            when (operation) {
                '+' -> a.add(b)
                '-' -> a.subtract(b)
                '*' -> a.multiply(b)
                '/' -> {
                    if (b.compareTo(BigDecimal.ZERO) == 0) {
                        showError()
                        return null
                    }
                    a.divide(b, MAX_DECIMAL_DIGITS, RoundingMode.HALF_UP)
                }
                else -> b
            }
        } catch (e: ArithmeticException) {
            showError()
            null
        }
    }

    /**
     * Resolve a operação pendente (equivalente ao "=" de uma calculadora comum).
     * Chamado internamente antes de usar o valor do visor para pagamento.
     */
    private fun resolvePendingOperation() {
        val op = pendingOperation ?: return
        val prev = previousOperand ?: return
        val current = currentInput.toBigDecimalOrNull() ?: return

        val result = calculate(prev, current, op) ?: return
        currentInput = formatBigDecimal(result)
        previousOperand = null
        pendingOperation = null
        shouldResetInput = true
    }

    private fun onClearPressed() {
        currentInput = "0"
        previousOperand = null
        pendingOperation = null
        shouldResetInput = true
        isInErrorState = false
        updateDisplay()
    }

    private fun showError() {
        isInErrorState = true
        currentInput = "0"
        previousOperand = null
        pendingOperation = null
        shouldResetInput = true
        binding.tvCalcDisplay.text = "Erro"
        Toast.makeText(this, "Não é possível dividir por zero.", Toast.LENGTH_SHORT).show()
    }

    // endregion

    // region Formatação / Exibição

    private fun updateDisplay() {
        if (!isInErrorState) {
            binding.tvCalcDisplay.text = currentInput
        }
    }

    private fun formatBigDecimal(value: BigDecimal): String {
        val rounded = value.setScale(MAX_DECIMAL_DIGITS, RoundingMode.HALF_UP).stripTrailingZeros()
        // Evita notação científica e mantém no máximo 2 casas decimais.
        return if (rounded.scale() <= 0) {
            rounded.toPlainString()
        } else {
            value.setScale(MAX_DECIMAL_DIGITS, RoundingMode.HALF_UP).toPlainString()
        }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = try {
        BigDecimal(this)
    } catch (e: NumberFormatException) {
        null
    }

    // endregion

    // region Retorno para PaymentValueActivity

    private fun onGoToPaymentPressed() {
        if (isInErrorState) {
            Toast.makeText(this, "Corrija o cálculo antes de continuar.", Toast.LENGTH_SHORT).show()
            return
        }

        // Garante que uma operação deixada "pendurada" (ex: usuário digitou
        // "10 + 5" e apertou direto em "Ir para Pagamento") seja resolvida.
        resolvePendingOperation()

        val finalValue = currentInput.toBigDecimalOrNull()
        if (finalValue == null || finalValue < BigDecimal.ZERO) {
            Toast.makeText(this, "Valor inválido.", Toast.LENGTH_SHORT).show()
            return
        }

        // Converte para centavos (Long) de forma segura, arredondando o que sobrar.
        val amountInCents = finalValue
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_AMOUNT_CENTS, amountInCents)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // endregion
}