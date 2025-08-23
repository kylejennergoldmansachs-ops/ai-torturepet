package com.example.aitorture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding

class StartupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
        }

        val title = TextView(this).apply {
            text = "AI TorturePet — First run setup (debug)"
            textSize = 18f
        }
        val apiKeyInput = EditText(this).apply {
            hint = "Mistral API Key"
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
        }
        val pixtralInput = EditText(this).apply {
            hint = "Pixtral Agent ID (default optional)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val summarizerInput = EditText(this).apply {
            hint = "Summarizer Agent ID (default optional)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val continueBtn = Button(this).apply { text = "Save & Continue" }

        root.addView(title)
        root.addView(apiKeyInput)
        root.addView(pixtralInput)
        root.addView(summarizerInput)
        root.addView(continueBtn)
        setContentView(root)

        apiKeyInput.setText(Storage.getApiKey(this) ?: "")
        pixtralInput.setText(Storage.getPixtralAgent(this) ?: "ag:ddacd900:20250823:untitled-agent:633c61ee")
        summarizerInput.setText(Storage.getSummarizerAgent(this) ?: "ag:ddacd900:20250823:untitled-agent:50c34ed9")

        continueBtn.setOnClickListener {
            val api = apiKeyInput.text.toString().trim()
            val pix = pixtralInput.text.toString().trim()
            val sum = summarizerInput.text.toString().trim()
            if (api.isNotEmpty()) {
                Storage.storeApiKey(this, api)
            }
            if (pix.isNotEmpty() && sum.isNotEmpty()) {
                Storage.storeAgentIds(this, pix, sum)
            }
            val it = Intent(this, MainActivity::class.java)
            startActivity(it)
            finish()
        }
    }
}
