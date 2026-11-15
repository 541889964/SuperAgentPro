
package com.nexus.pro.core
import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NetworkClient {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val BASE = "https://chat.deepseek.com"
    private val JSON_TYPE = "application/json".toMediaType()
    
    var token = ""; var smid = ""
    private var sId: String? = null
    private var pId: Any? = null

    private fun headers() = Headers.Builder().add("Authorization", "Bearer " + token).add("Content-Type", "application/json")
        .add("Accept", "text/event-stream").add("x-client-platform", "android").add("x-client-version", "2.8.0")
        .add("x-client-locale", "zh_CN").add("x-client-bundle-id", "com.deepseek.chat").add("User-Agent", "DeepSeek/2.8.0 Android/34")
        .add("x-device-id", smid).add("x-rangers-id", smid).build()

    suspend fun chat(prompt: String, enableThink: Boolean, onThink: (String) -> Unit, onContent: (String) -> Unit): String = withContext(Dispatchers.IO) {
        if (sId == null) {
            client.newCall(Request.Builder().url("$BASE/api/v0/chat_session/create").headers(headers()).post("{}".toRequestBody(JSON_TYPE)).build()).execute().use { r ->
                if (!r.isSuccessful) throw Exception("创建会话失败: ${r.code}")
                sId = JSONObject(r.body!!.string()).getJSONObject("data").getJSONObject("biz_data").getJSONObject("chat_session").getString("id")
            }
        }

        val powReq = Request.Builder().url("$BASE/api/v0/chat/create_pow_challenge").headers(headers()).post("{\"target_path\":\"/api/v0/chat/completion\"}".toRequestBody(JSON_TYPE)).build()
        val powHeader = client.newCall(powReq).execute().use { r ->
            if (!r.isSuccessful) throw Exception("PoW失败: ${r.code}")
            val ch = JSONObject(r.body!!.string()).getJSONObject("data").getJSONObject("biz_data").getJSONObject("challenge")
            val prefix = "${ch.getString("salt")}_${ch.getLong("expire_at")}_"
            val ans = PoW.solve(prefix, ch.getString("challenge"), ch.getInt("difficulty"))
            val obj = JSONObject().apply { put("algorithm", ch.getString("algorithm")); put("challenge", ch.getString("challenge")); put("salt", ch.getString("salt")); put("answer", ans); put("signature", ch.optString("signature")); put("target_path", "/api/v0/chat/completion") }
            Base64.encodeToString(obj.toString().toByteArray(), Base64.NO_WRAP)
        }

        val body = JSONObject().apply {
            put("chat_session_id", sId)
            put("parent_message_id", pId)
            put("prompt", prompt)
            put("thinking_enabled", enableThink)
            put("ref_file_ids", JSONArray())
            put("model_type", JSONObject.NULL)
            put("action", JSONObject.NULL)
            put("preempt", false)
            put("search_enabled", false)
            put("shumei_device_id", smid)
        }

        val req = Request.Builder().url("$BASE/api/v0/chat/completion").headers(headers().newBuilder().add("x-ds-pow-response", powHeader).build()).post(body.toString().toRequestBody(JSON_TYPE)).build()
        val response = client.newCall(req).execute()
        if (response.code == 429) throw Exception("RATE_LIMIT")
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val fragments = mutableMapOf<Int, JSONObject>()
        val finalResp = StringBuilder()
        var currentMode = "RESPONSE"

        response.body?.source()?.let { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                try {
                    val payload = JSONObject(line.substring(5).trim())
                    val p = payload.optString("p", ""); val o = payload.optString("o", ""); val v = payload.opt("v")
                    
                    if ("message_id" in payload) pId = payload.get("message_id")
                    if (payload.has("v") && payload.get("v") is JSONObject) {
                        val nested = payload.getJSONObject("v").optJSONObject("response")
                        if (nested != null) {
                            if (nested.has("message_id")) pId = nested.get("message_id")
                            val frags = nested.optJSONArray("fragments")
                            if (frags != null) {
                                fragments.clear()
                                for (i in 0 until frags.length()) {
                                    val f = frags.getJSONObject(i)
                                    fragments[i] = f
                                    if (f.optString("type") == "RESPONSE") currentMode = "RESPONSE"
                                }
                            }
                        }
                    }

                    if (p == "response/fragments" && v is JSONArray) {
                        fragments.clear()
                        for (i in 0 until v.length()) {
                            val frag = v.getJSONObject(i)
                            fragments[i] = frag
                            if (frag.optString("type") == "RESPONSE") currentMode = "RESPONSE"
                        }
                    } else if (o == "APPEND" && v is String && p.contains("/content")) {
                        val idxStr = Regex("fragments/(-?\\d+)/content").find(p)?.groupValues?.get(1) ?: "-1"
                        val idx = if (idxStr == "-1") (fragments.keys.maxOrNull() ?: 0) else idxStr.toInt()
                        if (!fragments.containsKey(idx)) fragments[idx] = JSONObject().put("type", currentMode).put("content", "")
                        fragments[idx]!!.put("content", fragments[idx]!!.optString("content") + v)
                    } else if (v is String && p.isEmpty() && o.isEmpty()) {
                        val idx = fragments.keys.maxOrNull() ?: 0
                        if (!fragments.containsKey(idx)) fragments[idx] = JSONObject().put("type", currentMode).put("content", v)
                        else fragments[idx]!!.put("content", fragments[idx]!!.optString("content") + v)
                    }

                    val currThink = fragments.values.filter { it.optString("type") == "THINK" }.joinToString("") { it.optString("content") }
                    val currResp = fragments.values.filter { it.optString("type") == "RESPONSE" }.joinToString("") { it.optString("content") }

                    if (currThink.isNotEmpty()) onThink(currThink.replace("**", ""))
                    if (currResp.isNotEmpty()) { finalResp.setLength(0); finalResp.append(currResp); onContent(currResp.replace("**", "")) }
                } catch (e: Exception) {}
            }
        }
        return finalResp.toString()
    }
}
