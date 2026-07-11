// ui/log/ComposeWrappers.kt
package cn.zjl.datacollector.ui.log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.zjl.datacollector.ui.theme.DataCollectorTheme

/**
 * Java 代码通过这个类调用 Compose 组件。
 * 所有方法加上 @JvmStatic 和 @Composable 注解。
 */
object ComposeWrappers {

    @JvmStatic
    fun launchOperationLogCenter(activity: ComponentActivity) {
        activity.setContent {
            DataCollectorTheme {
                OperationLogCenterScreen(
                    onNavigateBack = { activity.finish() }
                )
            }
        }
    }

    @JvmStatic
    fun launchDeviceDiagnostic(activity: ComponentActivity) {
        activity.setContent {
            DataCollectorTheme {
                cn.zjl.datacollector.ui.diagnostic.DeviceDiagnosticScreen(
                    onNavigateBack = { activity.finish() }
                )
            }
        }
    }
}
