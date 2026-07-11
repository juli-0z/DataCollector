package cn.zjl.datacollector.ui.diagnostic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zjl.datacollector.ui.theme.BluePrimaryDark
import cn.zjl.datacollector.ui.theme.Divider
import cn.zjl.datacollector.ui.theme.PageBackground
import cn.zjl.datacollector.ui.theme.SurfaceCardStrong
import cn.zjl.datacollector.ui.theme.TextPrimary
import cn.zjl.datacollector.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiagnosticScreen(
    viewModel: DeviceDiagnosticViewModel = viewModel(factory = DeviceDiagnosticViewModelFactory()),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备联调诊断") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.clearHistory() }) {
                        Text("清空记录", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BluePrimaryDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = PageBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(24.dp))}
            // ---- 连接 & WiFi 状态 ----
            item {
                SectionCard(title = "连接状态") {
                    LabelValue("TCP 状态", uiState.connectionState)
                    LabelValue("WiFi 状态", uiState.wifiState)
                    if (uiState.wifiSsid.isNotEmpty()) {
                        LabelValue("WiFi 热点", uiState.wifiSsid)
                    }
                    if (uiState.wifiDetail.isNotEmpty()) {
                        LabelValue("WiFi 详情", uiState.wifiDetail)
                    }
                }
            }

            // ---- 协议 & 握手 ----
            item {
                SectionCard(title = "协议状态") {
                    LabelValue("协议状态", uiState.protocolState)
                    LabelValue("握手阶段", uiState.handshakeStage)
                    LabelValue("目标地址", uiState.serverTarget)
                    LabelValue("重连次数", uiState.reconnectInfo)
                    LabelValue("最后命令", uiState.lastCommand)
                }
            }

            // ---- 报文统计 ----
            item {
                SectionCard(title = "报文统计") {
                    LabelValue("数据包", uiState.packetCountInfo)
                    LabelValue("帧统计", uiState.frameCountInfo)
                    LabelValue("校验错误", uiState.checksumCountInfo)
                    LabelValue("最新事件", uiState.lastEvent)
                    LabelValue("状态摘要", uiState.statusSummary)
                    LabelValue("更新时间", uiState.updatedAt)
                }
            }

            // ---- 发送报文 ----
            item {
                SectionCard(title = "最后发送") {
                    LabelValue("摘要", uiState.outgoingSummary)
                    LabelValue("时间", uiState.outgoingTime)
                    if (uiState.outgoingHex.isNotEmpty()) {
                        HexBlock(uiState.outgoingHex)
                    }
                }
            }

            // ---- 接收报文 ----
            item {
                SectionCard(title = "最后接收") {
                    LabelValue("摘要", uiState.incomingSummary)
                    LabelValue("时间", uiState.incomingTime)
                    if (uiState.incomingHex.isNotEmpty()) {
                        HexBlock(uiState.incomingHex)
                    }
                }
            }

            // ---- 异常帧 ----
            item {
                SectionCard(title = "异常记录") {
                    LabelValue("错误描述", uiState.lastError)
                    LabelValue("错误时间", uiState.errorTime)
                    if (uiState.errorFrameSummary.isNotEmpty()) {
                        LabelValue("异常帧摘要", uiState.errorFrameSummary)
                    }
                    LabelValue("异常帧时间", uiState.errorFrameTime)
                    if (uiState.errorFrameHex.isNotEmpty()) {
                        HexBlock(uiState.errorFrameHex)
                    }
                }
            }

            // ---- 事件日志 ----
            item {
                SectionCard(title = "事件日志") {
                    if (uiState.eventLog.isEmpty()) {
                        Text(
                            "暂无事件",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = uiState.eventLog,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }

            // bottom spacing
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardStrong),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Divider)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            content()
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    val displayValue = value.ifEmpty { "--" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = displayValue,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HexBlock(hex: String) {
    Text(
        text = hex,
        color = TextPrimary,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp,
            fontSize = 11.sp
        ),
        modifier = Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState())
    )
}
