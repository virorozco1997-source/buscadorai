package com.buscadorai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {

    companion object {
        val PROVIDERS = listOf("Tavily", "Perplexity", "Claude", "Gemini")
        val DEFAULT_MODELS = mapOf(
            "Tavily" to "basic",
            "Perplexity" to "sonar",
            "Claude" to "claude-sonnet-4-6",
            "Gemini" to "gemini-2.5-flash"
        )
        val HELP = mapOf(
            "Tavily" to "GRATIS, sin tarjeta. Key en app.tavily.com (empieza con tvly-). En 'Modelo' dejá basic.",
            "Perplexity" to "Key en perplexity.ai/settings/api (requiere crédito cargado)",
            "Claude" to "Key en console.anthropic.com (requiere crédito cargado)",
            "Gemini" to "Key en aistudio.google.com/apikey"
        )
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

    private fun provider() = prefs.getString("provider", "Tavily") ?: "Tavily"
    private fun keyOf(p: String) = prefs.getString("key_$p", "") ?: ""
    private fun modelOf(p: String) =
        prefs.getString("model_$p", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODELS[p]!!

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun c(id: Int) = getColor(id)
    private fun round(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

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

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(16))
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(results)
        }

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

        if (keyOf(provider()).isBlank()) settings()
    }

    private fun settings() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dlgTheme = if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val spin = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, PROVIDERS
            )
            setSelection(PROVIDERS.indexOf(provider()))
        }
        val help = TextView(this).apply {
            textSize = 13f
            setTextColor(c(R.color.text2))
            setPadding(0, dp(8), 0, 0)
        }
        val key = EditText(this).apply {
            hint = "API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply { hint = "Modelo" }

        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val n = PROVIDERS[pos]
                key.setText(keyOf(n))
                model.setText(modelOf(n))
                help.text = HELP[n]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        box.addView(spin); box.addView(help); box.addView(key); box.addView(model)

        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Configuración")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val n = PROVIDERS[spin.selectedItemPosition]
                prefs.edit()
                    .putString("provider", n)
                    .putString("key_$n", key.text.toString().trim())
                    .putString("model_$n", model.text.toString().trim())
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
        if (keyOf(provider()).isBlank()) { settings(); return }
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
        val p = provider()
        val key = keyOf(p)
        val model = modelOf(p)
        return when (p) {
            "Claude" -> askClaude(q, key, model)
            "Gemini" -> askGemini(q, key, model)
            "Perplexity" -> askPerplexity(q, key, model)
            else -> askTavily(q, key, model)
        }
    }

    // ---------- Tavily (gratis, 1000 búsquedas/mes) ----------
    private fun askTavily(q: String, key: String, depth: String): Result {
        val body = JSONObject()
            .put("query", q)
            .put("include_answer", true)
            .put("search_depth", if (depth == "advanced") "advanced" else "basic")
            .put("max_results", 5)
            .put("country", "argentina")
        val headers = mapOf("Authorization" to "Bearer $key")
        val url = "https://api.tavily.com/search"
        // Prioriza resultados de Argentina (más en español); si falla, reintenta sin eso.
        val res = try {
            post(url, headers, body)
        } catch (e: Exception) {
            body.remove("country")
            post(url, headers, body)
        }
        val rawAnswer = if (res.isNull("answer")) "" else res.optString("answer").trim()
        // Si la pregunta no está en inglés, se responde en español; si está en inglés, se deja tal cual.
        val lang = if (rawAnswer.isEmpty()) null else detectLang(q)
        val answer = if (lang != null && lang != "en") translate(rawAnswer, "es") else rawAnswer
        val sources = ArrayList<Source>()
        val rs = res.optJSONArray("results")
        if (rs != null) for (i in 0 until minOf(rs.length(), 5)) {
            val r = rs.getJSONObject(i)
            val u = r.optString("url")
            if (u.isNotEmpty()) sources.add(Source(r.optString("title", u), u))
        }
        val text = answer.ifEmpty {
            sources.firstOrNull()?.let { "Resultado más relevante:\n${it.url}" } ?: "Sin resultados."
        }
        return Result(text, sources)
    }

    // Traducción con el servicio gratuito (no oficial) de Google Translate.
    // Si falla por cualquier motivo, se devuelve el texto original.
    private fun gt(text: String, tl: String): JSONArray? = try {
        val conn = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$tl&dt=t")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("q=" + URLEncoder.encode(text, "UTF-8")).toByteArray()) }
        JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
    } catch (e: Exception) { null }

    private fun translate(text: String, tl: String): String {
        val arr = gt(text, tl)?.optJSONArray(0) ?: return text
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.optJSONArray(i)?.optString(0) ?: "")
        return sb.toString().ifBlank { text }
    }

    // Idioma detectado del texto (código como "en", "es"), o null si no se pudo.
    private fun detectLang(text: String): String? =
        gt(text, "en")?.optString(2)?.takeIf { it.isNotBlank() && it != "null" }

    // ---------- Perplexity (Sonar) ----------
    private fun askPerplexity(q: String, key: String, model: String): Result {
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM))
            .put(JSONObject().put("role", "user").put("content", q))
        val body = JSONObject().put("model", model).put("messages", msgs)
        val res = post(
            "https://api.perplexity.ai/chat/completions",
            mapOf("Authorization" to "Bearer $key"), body
        )
        val raw = res.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        val text = raw.replace(Regex("\\[\\d+\\]"), "").trim()

        val sources = ArrayList<Source>()
        val sr = res.optJSONArray("search_results")
        if (sr != null) for (i in 0 until minOf(sr.length(), 5)) {
            val r = sr.getJSONObject(i)
            val u = r.optString("url")
            if (u.isNotEmpty()) sources.add(Source(r.optString("title", u), u))
        }
        if (sources.isEmpty()) {
            val ci = res.optJSONArray("citations")
            if (ci != null) for (i in 0 until minOf(ci.length(), 5)) {
                val u = ci.optString(i)
                if (u.isNotEmpty()) sources.add(Source(Uri.parse(u).host ?: u, u))
            }
        }
        return Result(text.ifEmpty { "Sin resultados." }, sources)
    }

    // ---------- Claude (Anthropic) ----------
    private fun askClaude(q: String, key: String, model: String): Result {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", q))
        val tools = JSONArray().put(
            JSONObject().put("type", "web_search_20250305").put("name", "web_search").put("max_uses", 5)
        )
        val text = StringBuilder()
        val cited = LinkedHashMap<String, String>()
        val found = LinkedHashMap<String, String>()

        for (turn in 0 until 4) {
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1500)
                .put("system", SYSTEM)
                .put("messages", messages)
                .put("tools", tools)
            val res = post(
                "https://api.anthropic.com/v1/messages",
                mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body
            )
            val content = res.getJSONArray("content")
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                when (b.getString("type")) {
                    "text" -> {
                        text.append(b.getString("text"))
                        val cs = b.optJSONArray("citations")
                        if (cs != null) for (j in 0 until cs.length()) {
                            val c = cs.getJSONObject(j)
                            val u = c.optString("url")
                            if (u.isNotEmpty()) cited[u] = c.optString("title", u)
                        }
                    }
                    "web_search_tool_result" -> {
                        val c = b.opt("content")
                        if (c is JSONArray) for (j in 0 until c.length()) {
                            val r = c.getJSONObject(j)
                            val u = r.optString("url")
                            if (u.isNotEmpty()) found[u] = r.optString("title", u)
                        }
                    }
                }
            }
            if (res.optString("stop_reason") != "pause_turn") break
            messages.put(JSONObject().put("role", "assistant").put("content", content))
        }
        val srcs = if (cited.isNotEmpty()) cited else found
        return Result(
            text.toString().trim().ifEmpty { "Sin resultados." },
            srcs.entries.take(5).map { Source(it.value, it.key) }
        )
    }

    // ---------- Gemini (Google) ----------
    private fun askGemini(q: String, key: String, model: String): Result {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", q)))
            ))
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val res = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key), body
        )
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

    private fun resolve(u: String): String = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 5000; c.readTimeout = 5000
        val loc = if (c.responseCode in 300..399) c.getHeaderField("Location") else null
        c.disconnect()
        loc ?: u
    } catch (e: Exception) { u }

    // ---------- HTTP común ----------
    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 30000
        conn.readTimeout = 120000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = conn.responseCode in 200..299
        val s = (if (ok) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (!ok) {
            val msg = try {
                val e = JSONObject(s).opt("error")
                if (e is JSONObject) e.optString("message", s) else s
            } catch (ex: Exception) { s }
            throw Exception("HTTP ${conn.responseCode}: $msg")
        }
        return JSONObject(s)
    }
}
