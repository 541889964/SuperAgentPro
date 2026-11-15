
package com.nexus.pro.core
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

object RootHelper {
    suspend fun extractCredentials(): Pair<String, String> = withContext(Dispatchers.IO) {
        var token = ""; var smid = ""
        try {
            val process = ProcessBuilder("su", "-c", "cat /data/data/com.deepseek.chat/files/mmkv/mmkv.default").redirectErrorStream(true).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.ISO_8859_1))
            val sb = StringBuilder(); var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line).append("\n")
            process.waitFor()
            val text = sb.toString()
            Regex("[a-zA-Z0-9_\\-\\.]{64,200}\\.[a-zA-Z0-9_\\-\\.]{64,200}\\.[a-zA-Z0-9_\\-\\.]{64,200}").find(text)?.let { token = it.value }
            if (token.isEmpty()) Regex("\\b[a-zA-Z0-9+/=]{64,300}\\b").find(text)?.let { token = it.value }
            Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}").find(text)?.let { smid = it.value }
            if (smid.isEmpty()) Regex("\\b[a-fA-F0-9]{32,64}\\b").find(text)?.let { smid = it.value }
        } catch (e: Exception) {}
        token = token.filter { it.code in 0..127 }.trim()
        smid = smid.filter { it.code in 0..127 }.trim()
        if (smid.isEmpty()) smid = UUID.randomUUID().toString().replace("-", "")
        return@withContext Pair(token, smid)
    }
}
