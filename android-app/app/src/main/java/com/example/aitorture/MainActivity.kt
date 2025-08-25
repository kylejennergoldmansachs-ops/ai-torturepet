package com.example.aitorture

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : Activity() {
    private val TAG = "MainActivity"
    private val client = OkHttpClient()
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val key = Storage.getApiKey(this)
        if (key.isNullOrEmpty()) {
            startActivity(Intent(this, StartupActivity::class.java))
            finish()
            return
        }

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply { text = "AI TorturePet — Debug (Main)" }
        val btn = Button(this).apply { text = "Simulate user action (yank book)" }
        layout.addView(tv); layout.addView(btn)
        setContentView(layout)

        try {
            val ok = NativeBrain.nativeInit(20000, 8)
            Log.i(TAG, "Native init returned $ok")
            tv.append("\nNative init: $ok")
        } catch (e: Throwable) {
            Log.e(TAG, "Native init error: ${e.message}")
            tv.append("\nNative init error: ${e.message}")
        }

        // Start the foreground service correctly depending on OS level
        val svcIntent = Intent(this, ForegroundBrainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // On O+ use startForegroundService to ensure the system allows a foreground service start
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }

        btn.setOnClickListener {
            activityScope.launch {
                performCycle("I yank the AI's book roughly", mapOf("action" to "yank", "force" to 0.9))
            }
        }
    }

    private suspend fun performCycle(userText: String, sensorySnapshot: Map<String, Any>) = withContext(Dispatchers.IO) {
        try {
            val apiKey = Storage.getApiKey(applicationContext)!!
            val pixtralAgent = Storage.getPixtralAgent(applicationContext) ?: "ag:ddacd900:20250823:untitled-agent:633c61ee"

            val translatorPrompt = buildTranslatorPrompt(userText, sensorySnapshot)
            val translatorJson = callAgentInvoke(apiKey, pixtralAgent, translatorPrompt)
            Log.i(TAG, "Translator output raw: $translatorJson")

            val inputVec = convertTranslatorJsonToInputVector(translatorJson)

            NativeBrain.nativeApplyInputs(inputVec)

            for (i in 0 until 4) {
                NativeBrain.nativeStep()
            }

            val brainSummary = NativeBrain.nativeExportSummary()
            Log.i(TAG, "Brain summary: $brainSummary")

            val cognitivePrompt = buildCognitivePrompt(brainSummary, userText)
            val cognitiveJson = callAgentInvoke(apiKey, pixtralAgent, cognitivePrompt)
            Log.i(TAG, "Cognitive output raw: $cognitiveJson")

            val userFacing = parseCognitiveUserText(cognitiveJson)
            Log.i(TAG, "Final user-facing text: $userFacing")

        } catch (e: Exception) {
            Log.e(TAG, "performCycle error: ${e.message}")
        }
    }

    private fun buildTranslatorPrompt(userText: String, sensorySnapshot: Map<String, Any>): String {
        val ss = JSONObject(sensorySnapshot).toString()
        return """ 
You are a TRANSLATOR agent. Input: user's text and sensory snapshot.
Return ONLY valid JSON with fields:
- neural_inputs: array of { kind: "text_embedding"|"sensory_stim", tokens?: [ints], strength?: float, receptor?: string }
- neurogenesis: array of { label: string, cluster_size: int, seed_embedding?: [floats] }
- memory_flags: array of { summary: string, importance: float }
USER_TEXT: ${userText}
SENSORY_SNAPSHOT: ${ss}
"""
    }

    private fun buildCognitivePrompt(postBrainSummary: String, recentUserText: String): String {
        return """
You are the COGNITIVE agent (higher mind). Input: post brain summary and the user's last message.
Return ONLY JSON with fields:
- user_text: string
- behavior_directives: object
- archive: array of {summary: string, importance: float}

POST_BRAIN_SUMMARY: $postBrainSummary
RECENT_USER_TEXT: $recentUserText
"""
    }

    private fun callAgentInvoke(apiKey: String, agentId: String, prompt: String): String {
        val url = "https://api.mistral.ai/v1/agents/${java.net.URLEncoder.encode(agentId, "utf-8")}/invoke"
        val json = JSONObject()
        json.put("input", prompt)
        val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Agent invoke failed HTTP ${resp.code}: ${resp.message}")
            }
            val text = resp.body?.string() ?: ""
            return text
        }
    }

    private fun convertTranslatorJsonToInputVector(raw: String): FloatArray {
        val jsonObj = extractJsonObject(raw) ?: JSONObject()
        val inputSize = 256
        val vec = FloatArray(inputSize) { 0.0f }

        if (jsonObj.has("neural_inputs")) {
            val arr = jsonObj.optJSONArray("neural_inputs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val kind = item.optString("kind", "")
                if (kind == "text_embedding") {
                    if (item.has("tokens")) {
                        val tokens = item.optJSONArray("tokens")
                        for (t in 0 until tokens.length()) {
                            val tok = tokens.optInt(t, 0)
                            val idx = ((tok * 2654435761L) ushr 16).toInt() % inputSize
                            if (idx >= 0) vec[idx] = vec[idx] + (0.5f + (tok % 100) / 200f)
                        }
                    } else if (item.has("seed_embedding")) {
                        val emb = item.optJSONArray("seed_embedding")
                        for (k in 0 until emb.length()) {
                            val v = emb.optDouble(k, 0.0).toFloat()
                            val idx = k % inputSize
                            vec[idx] = vec[idx] + v * 0.1f
                        }
                    }
                    val strength = item.optDouble("strength", 1.0).toFloat()
                    for (j in vec.indices) vec[j] = vec[j] * strength
                } else if (kind == "sensory_stim") {
                    val receptor = item.optString("receptor", "default")
                    val intensity = item.optDouble("intensity", 1.0).toFloat()
                    val hash = abs(receptor.hashCode())
                    val baseIdx = (hash % inputSize)
                    val window = 6
                    for (w in 0 until window) {
                        val idx = (baseIdx + w) % inputSize
                        vec[idx] = vec[idx] + intensity * (1.0f - (w.toFloat()/window))
                    }
                }
            }
        }

        if (jsonObj.has("neurogenesis")) {
            val arr = jsonObj.optJSONArray("neurogenesis")
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val label = item.optString("label", "ng")
                val h = abs(label.hashCode())
                val idx = h % inputSize
                vec[idx] = vec[idx] + 1.0f
            }
        }

        var max = 0f
        for (v in vec) if (v > max) max = v
        if (max > 0f) {
            for (i in vec.indices) vec[i] = vec[i] / max
        }
        return vec
    }

    private fun extractJsonObject(raw: String): JSONObject? {
        val s = raw.trim()
        if (s.startsWith("{")) {
            try { return JSONObject(s) } catch (_: Exception) { }
        }
        val idx = s.indexOf('{')
        if (idx >= 0) {
            val sub = s.substring(idx)
            try { return JSONObject(sub) } catch (_: Exception) { }
        }
        val lastIdx = s.lastIndexOf('}')
        if (lastIdx >= 0) {
            val candidate = s.substring(0, lastIdx+1)
            val first = candidate.indexOf('{')
            if (first >= 0) {
                try { return JSONObject(candidate.substring(first)) } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun parseCognitiveUserText(raw: String): String {
        val jsonObj = extractJsonObject(raw)
        if (jsonObj != null && jsonObj.has("user_text")) {
            return jsonObj.optString("user_text", "(no text)")
        }
        val s = raw.trim()
        val sanitized = if (s.length > 400) s.substring(0, 400) + "..." else s
        return sanitized
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
