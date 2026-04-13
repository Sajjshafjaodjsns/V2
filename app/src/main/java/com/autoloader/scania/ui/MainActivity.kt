import android.view.View
import android.widget.TextView
package com.autoloader.scania.ui
import androidx.appcompat.R as AppCompatR

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.autoloader.scania.R
import com.autoloader.scania.model.UiState
import com.autoloader.scania.viewmodel.LoadPlanViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: LoadPlanViewModel by viewModels()

    private lateinit var toolbar: Toolbar
    private lateinit var inputContainer: LinearLayout
    private lateinit var btnCalculate: Button
    private lateinit var btnReset: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var resultContainer: LinearLayout
    private lateinit var tvError: TextView

    private val inputFields = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupToolbar()
        buildInputFields()
        setupListeners()
        observeState()
    }

    private fun bindViews() {
        toolbar         = findViewById(R.id.toolbar)
        inputContainer  = findViewById(R.id.inputContainer)
        btnCalculate    = findViewById(R.id.btnCalculate)
        btnReset        = findViewById(R.id.btnReset)
        progressBar     = findViewById(R.id.progressBar)
        tvProgress      = findViewById(R.id.tvProgress)
        resultContainer = findViewById(R.id.resultContainer)
        tvError         = findViewById(R.id.tvError)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Автовоз Scania 4×4"
        supportActionBar?.subtitle = "Установка Uçsuoğlu (2008)"
        toolbar.setTitleTextColor(0xFFFFFFFF.toInt())
        toolbar.setSubtitleTextColor(0xCCFFFFFF.toInt())
    }

    private fun buildInputFields() {
        repeat(8) { i ->
            val label = TextView(this).apply {
                text = "Авто ${i + 1}"
                setTextAppearance(R.style.TextAppearance_MaterialComponents_Body2)
                setPadding(4, if (i == 0) 0 else 20, 4, 2)
            }
            val field = EditText(this).apply {
                hint = "Марка Модель Год  (напр. Toyota Camry 2020)"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setBackgroundResource(R.drawable.bg_input)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(24, 20, 24, 20)
            }
            inputContainer.addView(label)
            inputContainer.addView(field)
            inputFields.add(field)
        }
    }

    private fun setupListeners() {
        btnCalculate.setOnClickListener {
            hideKeyboard()
            viewModel.calculate(inputFields.map { it.text.toString() })
        }
        btnReset.setOnClickListener {
            viewModel.reset()
            inputFields.forEach { it.text?.clear() }
            resultContainer.removeAllViews()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle    -> renderIdle()
                        is UiState.Loading -> renderLoading(state)
                        is UiState.Result  -> renderResult(state)
                        is UiState.Failure -> renderFailure(state.message)
                    }
                }
            }
        }
    }

    private fun renderIdle() {
        progressBar.visibility  = View.GONE
        tvProgress.visibility   = View.GONE
        resultContainer.visibility = View.GONE
        tvError.visibility      = View.GONE
        btnCalculate.isEnabled  = true
        btnReset.isEnabled      = false
    }

    private fun renderLoading(state: UiState.Loading) {
        progressBar.visibility  = View.VISIBLE
        tvProgress.visibility   = View.VISIBLE
        tvError.visibility      = View.GONE
        btnCalculate.isEnabled  = false
        btnReset.isEnabled      = true
        progressBar.progress    = state.progressPercent
        tvProgress.text         = state.message
    }

    private fun renderResult(state: UiState.Result) {
        progressBar.visibility  = View.GONE
        tvProgress.visibility   = View.GONE
        tvError.visibility      = View.GONE
        resultContainer.visibility = View.VISIBLE
        btnCalculate.isEnabled  = true
        btnReset.isEnabled      = true
        resultContainer.removeAllViews()
        ResultRenderer(this).render(state, resultContainer)
    }

    private fun renderFailure(message: String) {
        progressBar.visibility  = View.GONE
        tvProgress.visibility   = View.GONE
        resultContainer.visibility = View.GONE
        tvError.visibility      = View.VISIBLE
        btnCalculate.isEnabled  = true
        btnReset.isEnabled      = true
        tvError.text            = message
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
