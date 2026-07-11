package cn.zjl.datacollector.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import cn.zjl.datacollector.net.tcp.TcpDiagnosticsStore

class DeviceDiagnosticViewModel : ViewModel() {

    private val store = TcpDiagnosticsStore.getInstance()

    private val _uiState = MutableStateFlow(DeviceDiagnosticUiState())
    val uiState: StateFlow<DeviceDiagnosticUiState> = _uiState.asStateFlow()

    init {
        refresh()
        store.getLiveData().observeForever { snapshot ->
            if (snapshot != null) {
                _uiState.value = DeviceDiagnosticUiState.from(snapshot)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = store.getSnapshot()
            _uiState.value = DeviceDiagnosticUiState.from(snapshot)
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            store.clearHistory()
        }
    }
}

data class DeviceDiagnosticUiState(
    val connectionState: String = "",
    val wifiState: String = "",
    val wifiSsid: String = "",
    val wifiDetail: String = "",
    val protocolState: String = "",
    val handshakeStage: String = "",
    val serverTarget: String = "",
    val reconnectInfo: String = "",
    val lastCommand: String = "",
    val packetCountInfo: String = "",
    val frameCountInfo: String = "",
    val checksumCountInfo: String = "",
    val statusSummary: String = "",
    val lastEvent: String = "",
    val updatedAt: String = "",
    val outgoingSummary: String = "",
    val outgoingHex: String = "",
    val outgoingTime: String = "",
    val incomingSummary: String = "",
    val incomingHex: String = "",
    val incomingTime: String = "",
    val lastError: String = "",
    val errorTime: String = "",
    val errorFrameSummary: String = "",
    val errorFrameHex: String = "",
    val errorFrameTime: String = "",
    val eventLog: String = ""
) {
    companion object {
        fun from(snapshot: TcpDiagnosticsStore.Snapshot): DeviceDiagnosticUiState {
            return DeviceDiagnosticUiState(
                connectionState = labelForConnectionState(snapshot.connectionState),
                wifiState = labelForWifiState(snapshot.wifiState),
                wifiSsid = snapshot.wifiSsid,
                wifiDetail = snapshot.wifiDetail,
                protocolState = snapshot.protocolState?.displayName ?: "",
                handshakeStage = snapshot.handshakeStage,
                serverTarget = snapshot.serverTarget,
                reconnectInfo = "${snapshot.reconnectAttempts}/${snapshot.maxReconnectAttempts}",
                lastCommand = snapshot.lastCommand,
                packetCountInfo = "发送 ${snapshot.sentCommandCount} / 接收 ${snapshot.receivedPacketCount}",
                frameCountInfo = "状态帧 ${snapshot.statusFrameCount} / 波形帧 ${snapshot.waveformFrameCount}",
                checksumCountInfo = "${snapshot.checksumErrorCount}",
                statusSummary = snapshot.lastStatusSummary,
                lastEvent = snapshot.lastEvent,
                updatedAt = formatTime(snapshot.updatedAt),
                outgoingSummary = snapshot.lastOutgoingSummary,
                outgoingHex = snapshot.lastOutgoingHex,
                outgoingTime = formatTime(snapshot.lastOutgoingAt),
                incomingSummary = snapshot.lastIncomingSummary,
                incomingHex = snapshot.lastIncomingHex,
                incomingTime = formatTime(snapshot.lastIncomingAt),
                lastError = snapshot.lastError,
                errorTime = formatTime(snapshot.lastErrorAt),
                errorFrameSummary = snapshot.lastErrorFrameSummary,
                errorFrameHex = snapshot.lastErrorFrameHex,
                errorFrameTime = formatTime(snapshot.lastErrorFrameAt),
                eventLog = snapshot.recentEvents?.joinToString("\n\n") ?: ""
            )
        }

        private fun formatTime(timeMillis: Long): String {
            if (timeMillis <= 0L) return "--"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timeMillis))
        }

        private fun labelForConnectionState(state: cn.zjl.datacollector.net.tcp.TcpClientManager.ConnectionState?): String {
            return when (state) {
                cn.zjl.datacollector.net.tcp.TcpClientManager.ConnectionState.CONNECTING,
                cn.zjl.datacollector.net.tcp.TcpClientManager.ConnectionState.RECONNECTING -> "正在连接"
                cn.zjl.datacollector.net.tcp.TcpClientManager.ConnectionState.CONNECTED -> "已连接"
                else -> "未连接"
            }
        }

        private fun labelForWifiState(state: cn.zjl.datacollector.net.wifi.DeviceWifiState?): String {
            return when (state) {
                cn.zjl.datacollector.net.wifi.DeviceWifiState.CONNECTING -> "正在连接"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.CONNECTED -> "已连接"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.DISCONNECTED -> "已断开"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.PERMISSION_REQUIRED -> "缺少权限"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.UNAVAILABLE -> "未找到热点"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.LOST -> "热点已断开"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.ERROR -> "连接失败"
                cn.zjl.datacollector.net.wifi.DeviceWifiState.UNSUPPORTED -> "系统不支持"
                else -> "未启用"
            }
        }
    }
}

class DeviceDiagnosticViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DeviceDiagnosticViewModel() as T
    }
}
