// ui/log/OperationLogCenterScreen.kt
package cn.zjl.datacollector.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zjl.datacollector.ui.theme.BluePrimaryDark
import cn.zjl.datacollector.ui.theme.Divider
import cn.zjl.datacollector.ui.theme.Error
import cn.zjl.datacollector.ui.theme.PageBackground
import cn.zjl.datacollector.ui.theme.SurfaceCardStrong
import cn.zjl.datacollector.ui.theme.SurfaceChip
import cn.zjl.datacollector.util.safeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationLogCenterScreen(
    viewModel: OperationLogViewModel = viewModel(factory = OperationLogViewModelFactory(
        OperationLogStore(androidx.compose.ui.platform.LocalContext.current)
    )),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    // 新增：用于控制详情弹窗的状态
    var detailRecord by remember { mutableStateOf<OperationLogStore.LogRecord?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<OperationLogStore.LogRecord?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadHistory()  // ← 每次回到前台时刷新
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // === 清空确认弹窗（替换 AlertDialog） ===
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空操作日志") },
            text = { Text("仅清空本机的操作日志记录，不会修改工程数据库和上传状态。是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }


    if (showDeleteConfirm && recordToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                recordToDelete = null
            },
            title = { Text("删除日志") },
            text = { Text("确定要删除这条日志吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordToDelete?.let { viewModel.deleteRecord(it) }
                        showDeleteConfirm = false
                        recordToDelete = null
                        detailRecord = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Error  // 红色警示
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    recordToDelete = null
                }) {
                    Text("取消")
                }
            }
        )
    }
    // 详情弹窗
    detailRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { detailRecord = null },
            title = { Text(safeText(record.title)) },
            text = {
                val message = buildString {
                    append("时间：${OperationLogViewModel.formatTime(record.createdAt)}\n")
                    append("分类：${OperationLogViewModel.getCategoryLabel(record.category)}")
                    if (record.databaseName.isNotEmpty()) {
                        append("\n工程库：${record.databaseName}")
                    }
                    if (record.detail.isNotEmpty()) {
                        append("\n\n${record.detail}")
                    }
                }
                Text(message)
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            recordToDelete = record
                            showDeleteConfirm = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Error
                        )
                    ) {
                        Text("删除")
                    }
                    TextButton(onClick = { detailRecord = null }) {
                        Text("确定")
                    }
                }
            }
        )
    }

    // === 根布局：替代 CoordinatorLayout ===
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "操作日志中心",
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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
            item { Spacer(modifier = Modifier.height(16.dp)) }
            // === 摘要卡片 ===
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardStrong),
                    shape = RoundedCornerShape(24.dp) ,
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Surface(
                            color = SurfaceChip,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "采集 / 模板 / 上传 / 项目",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "查看最近的关键操作记录，便于现场追踪问题与回看流程",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "这里会汇总本机最近的连接、采集保存、参数模板、项目管理和手动上传等关键动作。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!uiState.isEmpty) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = uiState.summary,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiState.categorySummary,
                                modifier = Modifier.padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "还没有记录，后续的采集、模板和上传操作会显示在这里",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // === 日志列表卡片 ===
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardStrong),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Divider)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "最近记录",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            TextButton(
                                onClick = {
                                    if (!uiState.isEmpty) {
                                        showClearDialog = true
                                    }
                                },
                                enabled = !uiState.isEmpty
                            ) {
                                Text("清空记录")
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp))}

            // === 列表内容 ===
            if (uiState.isEmpty) {
                item {
                    Text(
                        text = "还没有操作日志记录",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = uiState.records,
                    key = { it.id }
                ) { record ->
                    OperationLogRecordItem(
                        record = record,
                        onClick = { detailRecord = record }
                    )
                }
            }
        }
    }
}

