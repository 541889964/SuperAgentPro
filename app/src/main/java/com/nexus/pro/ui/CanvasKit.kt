
package com.nexus.pro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.pro.core.AgentStep

@Composable
fun DrawKimiLogo(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier.size(64.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2)
        drawOval(color = Color.White, topLeft = Offset(size.width * 0.3f, size.height * 0.4f), size = Size(size.width * 0.12f, size.height * 0.2f))
        drawOval(color = Color.White, topLeft = Offset(size.width * 0.58f, size.height * 0.4f), size = Size(size.width * 0.12f, size.height * 0.2f))
    }
}

@Composable
fun DrawSendIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width / 2, size.height * 0.2f)
            lineTo(size.width * 0.8f, size.height * 0.6f)
            moveTo(size.width / 2, size.height * 0.2f)
            lineTo(size.width * 0.2f, size.height * 0.6f)
            moveTo(size.width / 2, size.height * 0.2f)
            lineTo(size.width / 2, size.height * 0.9f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun TimelineStepItem(step: AgentStep, accentColor: Color, textColor: Color, subTextColor: Color) {
    var expanded by remember { mutableStateOf(true) }

    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(20.dp).clip(CircleShape)
                    .background(if (step.isCompleted && !step.isError) Color(0xFF10B981) else if (step.isError) Color.Red else accentColor),
                contentAlignment = Alignment.Center
            ) {
                if (step.isCompleted) {
                    Canvas(Modifier.size(10.dp)) {
                        val p = Path().apply {
                            moveTo(size.width * 0.1f, size.height * 0.5f); lineTo(size.width * 0.4f, size.height * 0.8f); lineTo(size.width * 0.9f, size.height * 0.2f)
                        }
                        drawPath(p, Color.White, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
            Box(Modifier.width(2.dp).weight(1f).background(Color.White.copy(0.1f)))
        }

        Column(Modifier.weight(1f).padding(bottom = 24.dp, start = 8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("步骤 " + step.stepId, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.Text(if(expanded) "收起" else "展开", color = accentColor, fontSize = 12.sp)
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Text(step.thought, color = subTextColor, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Black).padding(12.dp)) {
                    androidx.compose.material3.Text("执行代码", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Text(step.command, color = Color(0xFFA5D6FF), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                if (step.result.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text("终端输出:", color = subTextColor, fontSize = 12.sp)
                    androidx.compose.material3.Text(step.result, color = if (step.isError) Color.Red else textColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
