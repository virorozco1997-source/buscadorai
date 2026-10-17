package com.buscadorai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val SYSTEM = "Sos un buscador. Respondé siempre en español, de forma directa y completa. " +
            "Si piden un link, buscalo en la web y dá la URL oficial completa. " +
            "Si piden información, dála completa en esta misma respuesta. " +
            "NUNCA hagas preguntas al usuario ni ofrezcas seguir; si algo es ambiguo, elegí la interpretación más probable y respondé. " +
            "No uses markdown (nada de asteriscos ni #), solo texto plano; escribí las URLs completas."
    }

    data class Source(val title: String, val url: String)
    data class Result(val text: String, val sources: List<Source>)

    private lateinit var input: EditText
    private lateinit var send: ImageButton
    private lateinit var results: LinearLayout
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(id: Int) = getColor(id)
    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Encabezado grande estilo One UI
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(24), dp(56), dp(16), dp(20))
        }
        val title = TextView(this).apply {
            text = "Buscador AI"
            textSize = 34f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(c(R.color.text))
        }
        val cfg = ImageButton(this).apply {
            setImageResource(R.drawable.ic_tune)
            setColorFilter(c(R.color.text))
            background = round(c(R.color.card), 24)
            setOnClickListener { settings() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(cfg, LinearLayout.LayoutParams(dp(48), dp(48)))

        // Resultados
        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(results)
        }

        // Barra de búsqueda tipo píldora
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = round(c(R.color.card), 32)
            setPadding(dp(22), dp(6), dp(8), dp(6))
        }
        input = EditText(this).apply {
            hint = "¿Qué buscás?"
            setHintTextColor(c(R.color.text2))
            setTextColor(c(R.color.text))
            textSize = 17f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            maxLines = 3
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
            }
        }
        send = ImageButton(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(c(R.color.on_accent))
            background = round(c(R.color.accent), 24)
            setOnClickListener { search() }
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        val barLp = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(16), dp(8), dp(16), dp(16))
        }

        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bar, barLp)
        setContentView(root)

        if (prefs.getString("key", "").isNullOrBlank()) settings()
    }

    private fun settings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val key = EditText(this).apply {
            hint = "API key de Google AI Studio"
            setText(prefs.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply {
            hint = "Modelo"
            setText(prefs.getString("model", DEFAULT_MODEL))
        }
        box.addView(key); box.addView(model)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Configuración")
            .setMessage("Conseguí una key gratis en aistudio.google.com/apikey")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit()
                    .putString("key", key.text.toString().trim())
                    .putString("model", model.text.toString().trim().ifEmpty { DEFAULT_MODEL })
                    .apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(c(R.color.card), 28)
        setPadding(dp(20), dp(18), dp(20), dp(18))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun showMessage(msg: String) {
        results.removeAllViews()
        val card = card()
        card.addView(TextView(this).apply {
            text = msg; textSize = 16f; setTextColor(c(R.color.text))
        })
        results.addView(card)
    }

    private fun showResult(r: Result) {
        results.removeAllViews()
        val answer = card()
        answer.addView(TextView(this).apply {
            text = r.text
            textSize = 17f
            setLineSpacing(0f, 1.15f)
            setTextColor(c(R.color.text))
            setLinkTextColor(c(R.color.accent))
            movementMethod = LinkMovementMethod.getInstance()
            Linkify.addLinks(this, Linkify.WEB_URLS)
        })
        results.addView(answer)

        if (r.sources.isNotEmpty()) {
            results.addView(TextView(this).apply {
                text = "Fuentes"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(c(R.color.text2))
                setPadding(dp(12), dp(10), 0, dp(8))
            })
            r.sources.forEach { s ->
                val item = card().apply {
                    setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.url)))
                    }
                }
                item.addView(TextView(this).apply {
                    text = s.title; textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(c(R.color.text))
                })
                item.addView(TextView(this).apply {
                    text = s.url; textSize = 13f
                    setTextColor(c(R.color.accent))
                    maxLines = 2
                })
                results.addView(item)
            }
        }
    }

    private fun search() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return
        if (prefs.getString("key", "").isNullOrBlank()) { settings(); return }
        send.isEnabled = false
        showMessage("Buscando...")
        Thread {
            try {
                val r = ask(q)
                runOnUiThread { showResult(r) }
            } catch (e: Exception) {
                runOnUiThread { showMessage("Error: ${e.message}") }
            }
            runOnUiThread { send.isEnabled = true }
        }.start()
    }

    private fun ask(q: String): Result {
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", q)))
            ))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val res = post(key, model, body)
        val cand = res.optJSONArray("candidates")?.optJSONObject(0)
            ?: return Result("Sin resultados.", emptyList())

        val text = StringBuilder()
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) for (i in 0 until parts.length()) text.append(parts.getJSONObject(i).optString("text"))

        val sources = ArrayList<Source>()
        val chunks = cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")
        if (chunks != null) for (i in 0 until minOf(chunks.length(), 5)) {
            val web = chunks.getJSONObject(i).optJSONObject("web") ?: continue
            val uri = web.optString("uri")
            if (uri.isEmpty()) continue
            sources.add(Source(web.optString("title", uri), resolve(uri)))
        }
        return Result(text.toString().trim().ifEmpty { "Sin resultados." }, sources)
    }

    // Los links de grounding son redirecciones; se resuelve el destino real.
    private fun resolve(u: String): String = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 5000; c.readTimeout = 5000
        val loc = if (c.responseCode in 300..399) c.getHeaderField("Location") else null
        c.disconnect()
        loc ?: u
    } catch (e: Exception) { u }

    private fun post(key: String, model: String, body: JSONObject): JSONObject {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-goog-api-key", key)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = conn.responseCode in 200..299
        val s = (if (ok) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (!ok) {
            val msg = try { JSONObject(s).getJSONObject("error").getString("message") } catch (e: Exception) { s }
            throw Exception("HTTP ${conn.responseCode}: $msg")
        }
        return JSONObject(s)
    }
}
