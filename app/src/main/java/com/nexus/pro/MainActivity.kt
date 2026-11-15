
package com.nexus.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.pro.core.*
import com.nexus.pro.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProApp() }
    }
}

@Composable
fun ProApp() {
    val bgColor = Color(0xFF0F0F12)
    val textColor = Color.White
    val subTextColor = Color(0xFFAAAAAA)
    val accentColor = Color(0xFF4D6BFE)

    MaterialTheme(colorScheme = darkColorScheme(background = bgColor)) {
        Scaffold(
            containerColor = bgColor,
            topBar = {
                Row(Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Agent Pro", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("防封已开启", color = Color(0xFF10B981), fontSize = 12.sp)
                }
            },
            bottomBar = { BottomInputBar(accentColor, textColor, subTextColor) }
        ) { padding ->
            MainContent(Modifier.padding(padding), textColor, subTextColor, accentColor)
        }
    }
}

object TaskStarter { var startTask: (String, Int, String) -> Unit = { _, _, _ -> } }

@Composable
fun MainContent(modifier: Modifier, textColor: Color, subTextColor: Color, accentColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { NetworkClient(context) }
    val loop = remember { AgentLoop(client) }
    
    var steps by remember { mutableStateOf(listOf<AgentStep>()) }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var isReady by remember { mutableStateOf(false) }
    
    var showDrawer by remember { mutableStateOf(false) }
    var sessions by remember { mutableStateOf(SessionManager.getSessions(context)) }

    LaunchedEffect(Unit) {
        val (t, s) = RootHelper.extractCredentials()
        client.token = t; client.smid = s
        if (t.isNotEmpty()) isReady = true
    }

    val startTask = { prompt: String, maxSteps: Int, thinkLevel: String ->
        if (isReady && !isRunning) {
            SessionManager.addSession(context, "temp_${System.currentTimeMillis()}", prompt.take(10))
            sessions = SessionManager.getSessions(context)
            
            steps = emptyList()
            isRunning = true
            statusMsg = "初始化..."
            loop.start(prompt, maxSteps, thinkLevel, scope) { ev ->
                when (ev) {
                    is AgentEvent.StepAdded -> steps = steps + ev.step
                    is AgentEvent.ResultUpdated -> steps = steps.map { if (it.stepId == ev.stepId) it.copy(result = ev.output, isCompleted = true, isError = ev.code != 0) else it }
                    is AgentEvent.StatusUpdate -> statusMsg = ev.msg
                    is AgentEvent.Done -> { isRunning = false; statusMsg = "任务完成" }
                    is AgentEvent.Error -> { isRunning = false; statusMsg = ev.msg }
                    else -> {}
                }
            }
        }
    }
    LaunchedEffect(Unit) { TaskStarter.startTask = startTask }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            if (steps.isEmpty() && !isRunning) {
                item {
                    Column(Modifier.fillParentMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        DrawKimiLogo(modifier = Modifier.size(80.dp), color = accentColor)
                        Spacer(Modifier.height(24.dp))
                        Text("Super Agent", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text("在下方设置参数后，直接下发任务", color = subTextColor, fontSize = 14.sp)
                    }
                }
            }

            items(steps, key = { step -> step.stepId }) { step ->
                TimelineStepItem(step, accentColor, textColor, subTextColor)
            }
            
            if (isRunning || statusMsg.isNotEmpty()) {
                item {
                    Row(Modifier.padding(vertical = 16.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(statusMsg, color = subTextColor, fontSize = 13.sp)
                    }
                }
            }
        }

        // 侧边栏 (会话管理)
        if (showDrawer) {
            Row(Modifier.fillMaxSize().background(Color.Black.copy(0.5f))) {
                Column(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF1E1E24)).padding(16.dp)) {
                    Text("历史会话", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    
                    LazyColumn(Modifier.weight(1f)) {
                        items(sessions) { session ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(session.title, color = textColor, fontSize = 14.sp, modifier = Modifier.clickable {
                                    client.resetSession()
                                    steps = emptyList()
                                    showDrawer = false
                                })
                                Text("删除", color = Color.Red, fontSize = 12.sp, modifier = Modifier.clickable { 
                                    SessionManager.deleteSession(context, session.sId)
                                    sessions = SessionManager.getSessions(context)
                                })
                            }
                        }
                    }
                    
                    Button(onClick = { showDrawer = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("关闭")
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().clickable { showDrawer = false })
            }
        }

        // 左上角菜单按钮 (悬浮)
        Box(Modifier.padding(top = 60.dp, start = 16.dp).size(36.dp).clip(CircleShape).background(Color(0xFF1E1E24)).clickable { showDrawer = true }, contentAlignment = Alignment.Center) {
            // 画三条横线作为菜单图标
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(16.dp).height(2.dp).background(textColor))
                Box(Modifier.width(16.dp).height(2.dp).background(textColor))
                Box(Modifier.width(16.dp).height(2.dp).background(textColor))
            }
        }
    }
}

@Composable
fun BottomInputBar(accentColor: Color, textColor: Color, subTextColor: Color) {
    var input by remember { mutableStateOf("") }
    var maxStepsStr by remember { mutableStateOf("0") }
    var thinkLevel by remember { mutableStateOf("中") }
    
    val levels = listOf("低", "中", "高", "极致")
    val panelColor = Color(0xFF1E1E24)

    Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                levels.forEach { level ->
                    Text(
                        level,
                        color = if (thinkLevel == level) accentColor else subTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (thinkLevel == level) accentColor.copy(0.2f) else Color.Transparent)
                            .clickable { thinkLevel = level }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("步数(0为无限):", color = subTextColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = maxStepsStr, 
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxStepsStr = it },
                    modifier = Modifier.width(60.dp).height(32.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(panelColor),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent
                ),
                placeholder = { Text("下达复杂任务...", color = subTextColor, fontSize = 14.sp) }
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(accentColor).clickable {
                if (input.isNotBlank()) {
                    TaskStarter.startTask(input, maxStepsStr.toIntOrNull() ?: 0, thinkLevel)
                    input = ""
                }
            }, contentAlignment = Alignment.Center) {
                DrawSendIcon(color = Color.White)
            }
        }
    }
}
