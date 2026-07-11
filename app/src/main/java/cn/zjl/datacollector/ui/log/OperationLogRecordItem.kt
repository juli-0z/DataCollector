// ui/log/OperationLogRecordItem.kt
package cn.zjl.datacollector.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.zjl.datacollector.ui.theme.Divider
import cn.zjl.datacollector.ui.theme.SurfaceCard
import cn.zjl.datacollector.ui.log.OperationLogViewModel.Companion.getCategoryLabel
import cn.zjl.datacollector.util.safeText

@Composable
fun OperationLogRecordItem(
    record: OperationLogStore.LogRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceCard
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Divider),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // 第一行：标题 + 分类 Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = safeText(record.title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 分类 Chip（对应 bg_chip_soft.xml 样式）
                AssistChip(
                    onClick = { /* 点击 Chip 可跳转到分类筛选，暂时不需要 */ },
                    label = {
                        Text(getCategoryLabel(record.category))
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 时间
            Text(
                text = OperationLogViewModel.formatTime(record.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 数据库名（条件显示）
            if (safeText(record.databaseName).isNotEmpty()) {
                Text(
                    text = record.databaseName,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 详情（条件显示）
            if (safeText(record.detail).isNotEmpty()) {
                Text(
                    text = record.detail,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 22.sp,
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
