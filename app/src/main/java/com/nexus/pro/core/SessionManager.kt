
package com.nexus.pro.core
import android.content.Context

data class ChatSession(val sId: String, val title: String, val time: Long = System.currentTimeMillis())
object SessionManager {
    private const val PREF = "agent_sessions"
    private const val KEY_SID = "current_sid"
    private const val KEY_PID = "current_pid"
    private const val KEY_LIST = "session_list"

    fun getCurrentId(ctx: Context): String? = ctx.getSharedPreferences(PREF, 0).getString(KEY_SID, null)
    fun getCurrentPid(ctx: Context): String? = ctx.getSharedPreferences(PREF, 0).getString(KEY_PID, null)
    fun saveCurrent(ctx: Context, sId: String, pId: String?) {
        ctx.getSharedPreferences(PREF, 0).edit().apply {
            putString(KEY_SID, sId)
            if (pId != null) putString(KEY_PID, pId)
        }.apply()
    }
    fun clearCurrent(ctx: Context) = ctx.getSharedPreferences(PREF, 0).edit().remove(KEY_SID).remove(KEY_PID).apply()

    fun getSessions(ctx: Context): List<ChatSession> {
        val raw = ctx.getSharedPreferences(PREF, 0).getString(KEY_LIST, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|||").mapNotNull {
            val parts = it.split("::")
            if (parts.size == 3) ChatSession(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L) else null
        }
    }
    fun addSession(ctx: Context, sId: String, title: String) {
        val list = getSessions(ctx).toMutableList()
        if (list.none { it.sId == sId }) {
            list.add(0, ChatSession(sId, title))
            saveSessions(ctx, list)
        }
    }
    fun deleteSession(ctx: Context, sId: String) {
        val list = getSessions(ctx).toMutableList()
        list.removeAll { it.sId == sId }
        saveSessions(ctx, list)
        if (getCurrentId(ctx) == sId) clearCurrent(ctx)
    }
    private fun saveSessions(ctx: Context, list: List<ChatSession>) {
        val raw = list.joinToString("|||") { "${it.sId}::${it.title.replace("::", "")}::${it.time}" }
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY_LIST, raw).apply()
    }
}
