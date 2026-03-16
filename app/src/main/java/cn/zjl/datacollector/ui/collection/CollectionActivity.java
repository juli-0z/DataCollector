package cn.zjl.datacollector.ui.collection;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.collection.CollectionManager;
import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.net.tcp.TcpClientManager;

/**
 * 数据采集主界面
 */
public class CollectionActivity extends AppCompatActivity {
    
    // UI 组件
    private Toolbar toolbar;
    private TextView textConnectionStatus;
    private TextView textBatteryVoltage;
    private TextView textCurrent;
    private TextView textTemperature;
    
    private EditText editPointNumber;
    private EditText editTransmitCurrent;
    private EditText editSampleFrequency;
    private EditText editCollectionCount;
    private EditText editSampleTime;
    private EditText editElectrodeDistance;
    
    private LineChart chartRecv;
    private LineChart chartSend;
    private LineChart chartOff;
    
    private Button btnConnect;
    private Button btnStartCollection;
    private Button btnStopCollection;
    private Button btnSave;
    private Button btnNextPoint;
    
    // 业务对象
    private TcpClientManager tcpClient;
    private CollectionManager collectionManager;
    
    private long projectId;
    private long surveyLineId = -1;
    private long currentPointId = -1;
    
    private boolean isCollecting = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);
        
        // 获取传入的参数
        projectId = getIntent().getLongExtra("project_id", -1);
        
        initViews();
        initTcpClient();
        initCollectionManager();
        setupCharts();
        setupListeners();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("数据采集");
        }
        
        textConnectionStatus = findViewById(R.id.text_connection_status);
        textBatteryVoltage = findViewById(R.id.text_battery_voltage);
        textCurrent = findViewById(R.id.text_current);
        textTemperature = findViewById(R.id.text_temperature);
        
        editPointNumber = findViewById(R.id.edit_point_number);
        editTransmitCurrent = findViewById(R.id.edit_transmit_current);
        editSampleFrequency = findViewById(R.id.edit_sample_frequency);
        editCollectionCount = findViewById(R.id.edit_collection_count);
        editSampleTime = findViewById(R.id.edit_sample_time);
        editElectrodeDistance = findViewById(R.id.edit_electrode_distance);
        
        chartRecv = findViewById(R.id.chart_recv);
        chartSend = findViewById(R.id.chart_send);
        chartOff = findViewById(R.id.chart_off);
        
        btnConnect = findViewById(R.id.btn_connect);
        btnStartCollection = findViewById(R.id.btn_start_collection);
        btnStopCollection = findViewById(R.id.btn_stop_collection);
        btnSave = findViewById(R.id.btn_save);
        btnNextPoint = findViewById(R.id.btn_next_point);
        
        // 初始状态
        updateUIState(false);
    }
    
    private void initTcpClient() {
        tcpClient = new TcpClientManager();
        tcpClient.setConnectionParams("192.168.1.100", 8080);  // 默认 IP
        
        tcpClient.setConnectionListener(new TcpClientManager.ConnectionListener() {
            @Override
            public void onConnectionStateChanged(TcpClientManager.ConnectionState state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case CONNECTED:
                            textConnectionStatus.setText("已连接");
                            textConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark));
                            btnConnect.setText("断开");
                            break;
                        case CONNECTING:
                            textConnectionStatus.setText("正在连接...");
                            textConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));
                            break;
                        case DISCONNECTED:
                            textConnectionStatus.setText("未连接");
                            textConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
                            btnConnect.setText("连接");
                            break;
                    }
                });
            }
            
            @Override
            public void onDataReceived(byte[] data) {
                // 处理接收到的数据
                if (collectionManager != null) {
                    collectionManager.processReceivedData(data);
                }
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(CollectionActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void initCollectionManager() {
        collectionManager = new CollectionManager(tcpClient);
        
        collectionManager.addDataCallback(new CollectionManager.DataCallback() {
            @Override
            public void onWaveformData(float[] timePoints, float[] recvValues, float[] sendValues, float[] offValues) {
                runOnUiThread(() -> {
                    updateChart(chartRecv, timePoints, recvValues, "Recv");
                    updateChart(chartSend, timePoints, sendValues, "Send");
                    updateChart(chartOff, timePoints, offValues, "Off");
                });
            }
            
            @Override
            public void onMonitorInfo(DeviceMonitorEntity monitor) {
                runOnUiThread(() -> {
                    textBatteryVoltage.setText(String.format("%.2f V", monitor.batteryVoltage));
                    textCurrent.setText(String.format("%.2f A", monitor.current));
                    textTemperature.setText(String.format("%.1f °C", monitor.temperature));
                });
            }
            
            @Override
            public void onCollectionComplete() {
                runOnUiThread(() -> {
                    Toast.makeText(CollectionActivity.this, "采集完成", Toast.LENGTH_SHORT).show();
                    isCollecting = false;
                    updateUIState(true);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(CollectionActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void setupCharts() {
        setupChart(chartRecv, "Recv 波形", "时间 (μs)", "电压 (V)");
        setupChart(chartSend, "Send 波形", "时间 (μs)", "电流 (A)");
        setupChart(chartOff, "Off 波形", "时间 (μs)", "电压 (V)");
    }
    
    private void setupChart(LineChart chart, String label, String xAxisLabel, String yAxisLabel) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(true);
        chart.getLegend().setEnabled(true);
        
        // X 轴设置
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        
        // Y 轴设置
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisRight().setEnabled(false);
        
        chart.invalidate();
    }
    
    private void updateChart(LineChart chart, float[] xValues, float[] yValues, String label) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(xValues.length, yValues.length); i++) {
            if (xValues[i] != 0 || yValues[i] != 0) {
                entries.add(new Entry(xValues[i], yValues[i]));
            }
        }
        
        if (entries.isEmpty()) {
            return;
        }
        
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(getColorByLabel(label));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        dataSet.setDrawValues(false);
        
        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);
        
        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);
        chart.invalidate();
    }
    
    private int getColorByLabel(String label) {
        switch (label) {
            case "Recv":
                return getColor(android.R.color.holo_blue_dark);
            case "Send":
                return getColor(android.R.color.holo_red_dark);
            case "Off":
                return getColor(android.R.color.holo_green_dark);
            default:
                return getColor(android.R.color.black);
        }
    }
    
    private void setupListeners() {
        // 连接按钮
        btnConnect.setOnClickListener(v -> {
            if (tcpClient.isConnected()) {
                tcpClient.disconnect();
            } else {
                // 可以弹出对话框输入 IP
                showIpDialog();
            }
        });
        
        // 开始采集
        btnStartCollection.setOnClickListener(v -> {
            if (!tcpClient.isConnected()) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
                return;
            }
            
            String pointNumberStr = editPointNumber.getText().toString().trim();
            if (pointNumberStr.isEmpty()) {
                Toast.makeText(this, "请输入测点编号", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 设置参数并开始采集
            CollectionParameterEntity params = new CollectionParameterEntity();
            params.transmitCurrent = parseFloat(editTransmitCurrent.getText().toString(), 25.0f);
            params.sampleFrequency = parseInt(editSampleFrequency.getText().toString(), 300);
            params.collectionCount = parseInt(editCollectionCount.getText().toString(), 2);
            params.sampleTime = parseFloat(editSampleTime.getText().toString(), 10.0f);
            params.electrodeDistance = parseFloat(editElectrodeDistance.getText().toString(), 0.0f);
            
            collectionManager.setParameters(params);
            collectionManager.startCollection();
            
            isCollecting = true;
            updateUIState(false);
        });
        
        // 停止采集
        btnStopCollection.setOnClickListener(v -> {
            if (isCollecting) {
                collectionManager.stopCollection();
            }
        });
        
        // 保存数据
        btnSave.setOnClickListener(v -> {
            // TODO: 保存数据到数据库
            Toast.makeText(this, "数据已保存", Toast.LENGTH_SHORT).show();
        });
        
        // 下一个测点
        btnNextPoint.setOnClickListener(v -> {
            String currentStr = editPointNumber.getText().toString().trim();
            if (!currentStr.isEmpty()) {
                try {
                    int nextNumber = Integer.parseInt(currentStr) + 1;
                    editPointNumber.setText(String.valueOf(nextNumber));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        });
    }
    
    private void showIpDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tcp_config, null);
        EditText editIp = dialogView.findViewById(R.id.edit_tcp_ip);
        EditText editPort = dialogView.findViewById(R.id.edit_tcp_port);
        editIp.setText("192.168.1.100");
        editPort.setText("8080");
        
        new AlertDialog.Builder(this)
            .setTitle("TCP 连接设置")
            .setView(dialogView)
            .setPositiveButton("连接", (dialog, which) -> {
                String ip = editIp.getText().toString().trim();
                String portStr = editPort.getText().toString().trim();
                
                try {
                    int port = Integer.parseInt(portStr);
                    tcpClient.setConnectionParams(ip, port);
                    tcpClient.connect();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "端口号格式错误", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void updateUIState(boolean connected) {
        btnStartCollection.setEnabled(connected && !isCollecting);
        btnStopCollection.setEnabled(connected && isCollecting);
        btnSave.setEnabled(!isCollecting);
        
        editPointNumber.setEnabled(!isCollecting);
        editTransmitCurrent.setEnabled(!isCollecting);
        editSampleFrequency.setEnabled(!isCollecting);
        editCollectionCount.setEnabled(!isCollecting);
        editSampleTime.setEnabled(!isCollecting);
        editElectrodeDistance.setEnabled(!isCollecting);
    }
    
    private int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private float parseFloat(String str, float defaultValue) {
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // 返回键点击事件
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) {
            tcpClient.disconnect();
        }
    }
}
