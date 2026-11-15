
package com.nexus.pro.core
import kotlinx.coroutines.*
import kotlin.random.Random

data class AgentStep(
    val stepId: Int,
    var thought: String = "",
    var command: String = "",
    var result: String = "",
    var isCompleted: Boolean = false,
    var isError: Boolean = false
)

sealed class AgentEvent {
    data class StepAdded(val step: AgentStep) : AgentEvent()
    data class ResultUpdated(val stepId: Int, val output: String, val code: Int) : AgentEvent()
    data class StatusUpdate(val msg: String) : AgentEvent()
    object Done : AgentEvent()
    data class Error(val msg: String) : AgentEvent()
}

class AgentLoop(private val client: NetworkClient) {
    private var job: Job? = null
    private var stepCounter = 0

    fun start(userPrompt: String, maxSteps: Int, thinkLevel: String, scope: CoroutineScope, onEvent: (AgentEvent)->Unit) {
        job = scope.launch(Dispatchers.IO) {
            val thinkRule = when(thinkLevel) {
                "低" -> "极度精简，一句话带过。"
                "中" -> "简明扼要，指出下一步目标即可。"
                "高" -> "详细分析上一步的执行结果，思考可能的错误和下一步行动。"
                "极致" -> "推演所有可能的边界情况、报错原因，并准备好 Plan B。"
                else -> ""
            }

            val sysPrompt = listOf(
                "你是一个高级 AI Agent，拥有本地终端执行权限。",
                "任务：" + userPrompt,
                "",
                "【思考强度要求】" + thinkRule,
                "",
                "你必须分步骤执行，每次回复必须严格使用以下两种格式之一：",
                "格式 1：",
                "【思考】你的推理过程。",
                "【执行】",
                "```bash或python",
                "命令代码",
                "```",
                "",
                "格式 2：",
                "【完成】总结你的工作。"
            ).joinToString("\n")

            var currentPrompt = sysPrompt
            stepCounter = 0
            var retryCount = 0
            
            while (isActive) {
                try {
                    if (maxSteps > 0 && stepCounter >= maxSteps) {
                        onEvent(AgentEvent.StatusUpdate("已达最大步数限制 (" + maxSteps + ")，强制停止。"))
                        onEvent(AgentEvent.Done)
                        break
                    }
                    
                    val remainMsg = if (maxSteps == 0) "无限" else (maxSteps - stepCounter).toString()
                    onEvent(AgentEvent.StatusUpdate("正在请求 AI... (剩余步数: " + remainMsg + ")"))

                    // 防封优化：基础随机延迟 1.5s - 4s
                    val baseDelay = Random.nextLong(1500L, 4000L)
                    delay(baseDelay)

                    val fullText = client.chat(currentPrompt, true, onThink = { }, onContent = { })

                    if (fullText.contains("【完成】")) {
                        onEvent(AgentEvent.Done)
                        break
                    }

                    val thoughtMatch = Regex("【思考】\\\\n(.*?)(?=\\\\n【执行】)", RegexOption.DOT_MATCHES_ALL).find(fullText)
                    val actionMatch = Regex("【执行】\\\\n```(?:bash|sh|python|py)?\\\\n(.*?)\\\\n```", RegexOption.DOT_MATCHES_ALL).find(fullText)

                    val thought = thoughtMatch?.groupValues?.get(1)?.trim() ?: "分析任务中..."
                    val command = actionMatch?.groupValues?.get(1)?.trim()

                    if (command != null && command.isNotEmpty()) {
                        stepCounter++
                        val step = AgentStep(stepCounter, thought = thought, command = command)
                        onEvent(AgentEvent.StepAdded(step))

                        onEvent(AgentEvent.StatusUpdate("正在通过 Termux 执行命令..."))
                        val ext = if (command.contains("python") || command.contains("import ")) "py" else "sh"
                        val filepath = "/sdcard/Download/nexus_step_" + stepCounter + "." + ext
                        java.io.File(filepath).writeText(command)
                        val execCmd = if (ext == "py") "python3 " + filepath else "bash " + filepath
                        
                        val result = RootExecutor.executeInTermux(execCmd)
                        onEvent(AgentEvent.ResultUpdated(step.stepId, result.output, result.exitCode))
                        
                        currentPrompt = "上一步执行结果 (exit=" + result.exitCode + "):\n" + result.output + "\n请根据结果继续输出下一步。"
                    } else {
                        onEvent(AgentEvent.StatusUpdate("AI 格式错误，正在要求重试..."))
                        currentPrompt = "格式错误！必须严格按【思考】+【执行】(代码块) 或 【完成】输出。请重试。"
                    }
                    retryCount = 0
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e.message == "RATE_LIMIT") {
                        if (retryCount >= 5) { onEvent(AgentEvent.Error("限流重试耗尽")); break }
                        val backoff = 10000L * (1L shl retryCount)
                        onEvent(AgentEvent.StatusUpdate("触发限流，冷却等待 " + (backoff/1000) + "s"))
                        delay(backoff)
                        retryCount++
                    } else {
                        onEvent(AgentEvent.Error("异常: " + e.message)); break
                    }
                }
            }
        }
    }
    fun cancel() { job?.cancel(); RootExecutor.interrupt() }
}
