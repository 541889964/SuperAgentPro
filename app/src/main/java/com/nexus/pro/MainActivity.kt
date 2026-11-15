
package com.nexus.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
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
        MainScreen(textColor, subTextColor, accentColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(textColor: Color, subTextColor: Color, accentColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { NetworkClient(context) }
    val loop = remember { AgentLoop(client) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isRunning by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf(SessionManager.getSessions(context)) }

    LaunchedEffect(Unit) {
        val (t, s) = RootHelper.extractCredentials()
        client.token = t; client.smid = s
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) { listState.animateScrollToItem(messages.size - 1) }
    }

    val startTask = { prompt: String, maxSteps: Int, thinkLevel: String ->
        if (!isRunning) {
            SessionManager.addSession(context, "temp_${System.currentTimeMillis()}", prompt.take(10))
            sessions = SessionManager.getSessions(context)
            messages = messages + ChatMessage(role = "user", content = prompt)
            isRunning = true
            
            loop.start(prompt, maxSteps, thinkLevel, scope) { ev ->
                when (ev) {
                    is AgentEvent.MessageCreated -> messages = messages + ev.msg
                    is AgentEvent.StreamUpdate -> {
                        messages = messages.map {
                            if (it.id == ev.msgId) {
                                if (ev.isThink) it.copy(thinking = it.thinking + ev.textDelta)
                                else it.copy(content = it.content + ev.textDelta)
                            } else it
                        }
                    }
                    is AgentEvent.CommandExtracted -> {
                        messages = messages.map { if (it.id == ev.msgId) it.copy(command = ev.cmd, isExecuting = true) else it }
                    }
                    is AgentEvent.ExecutionFinished -> {
                        messages = messages.map { if (it.id == ev.msgId) it.copy(result = ev.result, isExecuting = false) else it }
                    }
                    is AgentEvent.StatusUpdate -> statusMsg = ev.msg
                    is AgentEvent.Done -> { isRunning = false; statusMsg = "" }
                    is AgentEvent.Error -> { isRunning = false; statusMsg = "报错: " + ev.msg }
                    else -> {}
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFF1E1E24)) {
                Column(Modifier.padding(16.dp).fillMaxHeight()) {
                    Text("历史会话", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(sessions) { session ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(session.title, color = textColor, fontSize = 14.sp, modifier = Modifier.clickable {
                                    client.loadSession(session.sId, null)
                                    messages = emptyList() 
                                    scope.launch { drawerState.close() }
                                })
                                Text("删除", color = Color.Red, fontSize = 12.sp, modifier = Modifier.clickable { 
                                    SessionManager.deleteSession(context, session.sId)
                                    sessions = SessionManager.getSessions(context)
                                })
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = Color(0xFF0F0F12),
            topBar = {
                TopAppBar(
                    title = { Text("Agent Pro", color = textColor, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu", tint = textColor) } },
                    actions = { 
                        // 🚨 实时显示冷却或运行状态
                        Text(if (isRunning) "执行中" else "已就绪", color = accentColor, fontSize = 12.sp, modifier = Modifier.padding(end = 16.dp)) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F0F12))
                )
            },
            bottomBar = { BottomInputBar(accentColor, textColor, subTextColor, isRunning, startTask) }
        ) { padding ->
            LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 12.dp)) {
                if (messages.isEmpty()) {
                    item {
                        Column(Modifier.fillParentMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            DrawKimiLogo(modifier = Modifier.size(80.dp), color = accentColor)
                            Spacer(Modifier.height(24.dp))
                            Text("你好，我是 Super Agent", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("我可以写代码、执行命令，也能陪你聊天。", color = subTextColor, fontSize = 14.sp)
                        }
                    }
                }

                items(messages) { msg ->
                    ChatBubble(msg, accentColor, textColor, subTextColor)
                }
                
                // 🚨 底部状态提示（显示报错或冷却倒计时）
                if (isRunning && statusMsg.isNotEmpty()) {
                    item {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accentColor, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(statusMsg, color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, accentColor: Color, textColor: Color, subTextColor: Color) {
    val isUser = msg.role == "user"
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(accentColor).align(Alignment.Top), contentAlignment = Alignment.Center) {
                Text("A", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(16.dp, if (isUser) 0.dp else 16.dp, 16.dp, if (isUser) 16.dp else 0.dp))
                .background(if (isUser) accentColor else Color(0xFF1E1E24))
                .padding(12.dp)
        ) {
            if (msg.thinking.isNotEmpty()) {
                Column(Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.3f)).padding(8.dp)) {
                    Text("深度思考中...", color = Color.Gray, fontSize = 11.sp)
                    Text(msg.thinking, color = Color.LightGray, fontSize = 13.sp)
                }
            }
            
            if (msg.content.isNotEmpty()) {
                Text(msg.content, color = Color.White, fontSize = 15.sp)
            }

            // 终端执行卡片 (代码 + 结果)
            if (msg.command.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black).border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("代码执行", color = Color.Gray, fontSize = 11.sp)
                    Text(msg.command, color = Color(0xFFA5D6FF), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    
                    if (msg.isExecuting) {
                        Spacer(Modifier.height(4.dp))
                        Text("正在执行中...", color = accentColor, fontSize = 12.sp)
                    } else if (msg.result.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Divider(color = Color.White.copy(0.1f))
                        Spacer(Modifier.height(8.dp))
                        Text("输出:", color = Color.Gray, fontSize = 11.sp)
                        Text(msg.result, color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun BottomInputBar(accentColor: Color, textColor: Color, subTextColor: Color, isRunning: Boolean, startTask: (String, Int, String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var maxStepsStr by remember { mutableStateOf("0") }
    var thinkLevel by remember { mutableStateOf("中") }
    val levels = listOf("低", "中", "高", "极致")
    val panelColor = Color(0xFF1E1E24)

    Column(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                levels.forEach { level ->
                    Text(level, color = if (thinkLevel == level) accentColor else subTextColor, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (thinkLevel == level) accentColor.copy(0.2f) else Color.Transparent).clickable { thinkLevel = level }.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("步数:", color = subTextColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                TextField(value = maxStepsStr, onValueChange = { if (it.all { char -> char.isDigit() }) maxStepsStr = it },
                    modifier = Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp)).background(panelColor),
                colors = TextFieldDefaults.colors(unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedIndicatorColor = Color.Transparent),
                placeholder = { Text("输入指令，或向 AI 提问...", color = subTextColor, fontSize = 14.sp) })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (isRunning) Color.Gray else accentColor).clickable {
                if (input.isNotBlank() && !isRunning) {
                    startTask(input, maxStepsStr.toIntOrNull() ?: 0, thinkLevel)
                    input = ""
                }
            }, contentAlignment = Alignment.Center) { DrawSendIcon(color = Color.White) }
        }
    }
}
