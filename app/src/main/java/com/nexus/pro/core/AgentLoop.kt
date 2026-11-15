
package com.nexus.pro.core
import kotlinx.coroutines.*
import kotlin.random.Random

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val role: String, 
    var content: String = "",
    var thinking: String = "",
    var command: String = "",
    var result: String = "",
    var isExecuting: Boolean = false
)

sealed class AgentEvent {
    data class MessageCreated(val msg: ChatMessage) : AgentEvent()
    data class StreamUpdate(val msgId: String, val textDelta: String, val isThink: Boolean) : AgentEvent()
    data class CommandExtracted(val msgId: String, val cmd: String) : AgentEvent()
    data class ExecutionFinished(val msgId: String, val result: String, val isError: Boolean) : AgentEvent()
    data class StatusUpdate(val msg: String) : AgentEvent()
    object Done : AgentEvent()
    data class Error(val msg: String) : AgentEvent()
}

class AgentLoop(private val client: NetworkClient) {
    private var job: Job? = null

    fun start(userPrompt: String, maxSteps: Int, thinkLevel: String, scope: CoroutineScope, onEvent: (AgentEvent)->Unit) {
        job = scope.launch(Dispatchers.IO) {
            val msgId = System.currentTimeMillis().toString()
            onEvent(AgentEvent.MessageCreated(ChatMessage(id = msgId, role = "assistant")))
            
            val sysPrompt = "你是一个智能 AI 助手。如果是普通问题，直接回答。\n" +
                "如果需要执行代码，请使用 Markdown 代码块，并标明语言，例如：\n" +
                "【执行】\n```python\nprint('hello')\n```\n" +
                "支持的语言有：bash, python, javascript, node, c, cpp 等。"

            var currentPrompt = sysPrompt + "\n\n用户：" + userPrompt
            var retryCount = 0
            
            while (isActive) {
                try {
                    val baseDelay = Random.nextLong(1000L, 2500L)
                    delay(baseDelay)

                    val fullText = client.chat(
                        prompt = currentPrompt, 
                        enableThink = true, 
                        onStatus = { onEvent(AgentEvent.StatusUpdate(it)) },
                        onThink = { onEvent(AgentEvent.StreamUpdate(msgId, it, isThink = true)) },
                        onContent = { onEvent(AgentEvent.StreamUpdate(msgId, it, isThink = false)) }
                    )

                    // 🚨 动态解析语言和后缀
                    val actionMatch = Regex("【执行】\\n```([a-zA-Z0-9]+)\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL).find(fullText)
                    
                    if (actionMatch != null) {
                        val lang = actionMatch.groupValues[1].lowercase().trim()
                        val command = actionMatch.groupValues[2].trim()
                        
                        // 决定执行命令和文件后缀
                        var ext = "txt"
                        var execCmd = ""
                        when (lang) {
                            "python", "py" -> { ext = "py"; execCmd = "python3" }
                            "bash", "sh", "shell" -> { ext = "sh"; execCmd = "bash" }
                            "javascript", "js", "node" -> { ext = "js"; execCmd = "node" }
                            "c" -> { ext = "c"; execCmd = "gcc" }
                            "cpp", "c++" -> { ext = "cpp"; execCmd = "g++" }
                            else -> { ext = "sh"; execCmd = "bash" }
                        }

                        onEvent(AgentEvent.CommandExtracted(msgId, command))

                        onEvent(AgentEvent.StatusUpdate("正在执行 $lang 代码..."))
                        
                        // 🚨 核心修复：将脚本写入 Termux 的缓存目录，绝对有执行权限
                        val filepath = "/data/data/com.termux/files/home/.cache/nexus_script_" + System.currentTimeMillis() + "." + ext
                        
                        // 如果是 C/C++ 需要特殊处理（编译）
                        var finalExecCmd = ""
                        if (lang == "c") {
                            val binpath = filepath.replace(".c", "")
                            finalExecCmd = "gcc $filepath -o $binpath && $binpath"
                        } else if (lang == "cpp" || lang == "c++") {
                            val binpath = filepath.replace(".cpp", "")
                            finalExecCmd = "g++ $filepath -o $binpath && $binpath"
                        } else {
                            finalExecCmd = "$execCmd $filepath"
                        }

                        val fullCmd = "mkdir -p /data/data/com.termux/files/home/.cache && cat << 'NEXUS_EOF' > $filepath\n$command\nNEXUS_EOF\n$finalExecCmd"
                        
                        val result = RootExecutor.executeInTermux(fullCmd)
                        onEvent(AgentEvent.ExecutionFinished(msgId, result.output, result.exitCode != 0))
                        
                        // 把结果发给 AI 看
                        currentPrompt = sysPrompt + "\n\n用户：$userPrompt\n\n你刚才执行的命令结果 (exit=${result.exitCode}):\n${result.output}\n请根据执行结果，直接给用户最终答复。"
                    } else {
                        break
                    }
                    retryCount = 0
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e.message == "RATE_LIMIT") {
                        if (retryCount >= 5) { onEvent(AgentEvent.Error("限流重试耗尽")); break }
                        val backoff = 10000L * (1L shl retryCount)
                        
                        // 🚨 每秒更新冷却倒计时
                        var remainingSec = backoff / 1000
                        while (remainingSec > 0) {
                            onEvent(AgentEvent.StatusUpdate("触发防封冷却限制，等待 $remainingSec 秒..."))
                            delay(1000)
                            remainingSec--
                        }
                        retryCount++
                    } else {
                        onEvent(AgentEvent.Error("异常: " + e.message)); break
                    }
                }
            }
            onEvent(AgentEvent.Done)
        }
    }
    fun cancel() { job?.cancel(); RootExecutor.interrupt() }
}
