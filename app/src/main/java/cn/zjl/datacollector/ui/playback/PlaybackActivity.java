package cn.zjl.datacollector.ui.playback;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.zjl.datacollector.R;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.entity.WaveformDataEntity;
import cn.zjl.datacollector.data.repository.DataRepository;

/**
 * 数据回放界面
 */
public class PlaybackActivity extends AppCompatActivity {
    
    private Spinner spinnerPoints;
    private LineChart chartPlayback;
    private SeekBar seekBarTime;
    private TextView textTimeInfo;
    private Button btnPlay;
    private Button btnPause;
    private Button btnStop;
    
    private DataRepository dataRepository;
    private List<MeasurementPointEntity> points;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPlaying = false;
    private int currentPosition = 0;
    private Runnable playRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playback);
        
        String databasePath = getIntent().getStringExtra("database_path");
        
        initViews();
        dataRepository = new DataRepository(this);
        loadPoints(databasePath);
        setupSeekBar();
    }
    
    private void initViews() {
        spinnerPoints = findViewById(R.id.spinner_points);
        chartPlayback = findViewById(R.id.chart_playback);
        seekBarTime = findViewById(R.id.seekbar_time);
        textTimeInfo = findViewById(R.id.text_time_info);
        
        btnPlay = findViewById(R.id.btn_play);
        btnPause = findViewById(R.id.btn_pause);
        btnStop = findViewById(R.id.btn_stop);
        
        // 选择测点
        spinnerPoints.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (points != null && position < points.size()) {
                    MeasurementPointEntity point = points.get(position);
                    loadWaveformForPoint(point.id);
                }
            }
            
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // 播放控制
        btnPlay.setOnClickListener(v -> startPlayback());
        btnPause.setOnClickListener(v -> pausePlayback());
        btnStop.setOnClickListener(v -> stopPlayback());
    }
    
    private void loadPoints(String databasePath) {
        // 从数据库加载所有测点
        // 这里简化处理，实际应该根据 databasePath 查询对应的数据库
        new Thread(() -> {
            // TODO: 实现从指定数据库加载测点
            // 暂时使用模拟数据
            runOnUiThread(() -> {
                List<String> pointNames = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    pointNames.add("测点 " + (i + 1));
                }
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, pointNames);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerPoints.setAdapter(adapter);
            });
        }).start();
    }
    
    private void loadWaveformForPoint(long pointId) {
        new Thread(() -> {
            // TODO: 从数据库加载波形数据
            // 这里使用模拟数据演示
            float[] timePoints = new float[100];
            float[] values = new float[100];
            
            for (int i = 0; i < 100; i++) {
                timePoints[i] = i * 0.1f;
                values[i] = (float) Math.sin(i * 0.1) * 10;
            }
            
            runOnUiThread(() -> {
                displayWaveform(timePoints, values);
            });
        }).start();
    }
    
    private void displayWaveform(float[] timePoints, float[] values) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(timePoints.length, values.length); i++) {
            entries.add(new Entry(timePoints[i], values[i]));
        }
        
        LineDataSet dataSet = new LineDataSet(entries, "波形");
        dataSet.setColor(getColor(android.R.color.holo_blue_dark));
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);
        
        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);
        
        LineData lineData = new LineData(dataSets);
        chartPlayback.setData(lineData);
        chartPlayback.invalidate();
        
        // 设置 SeekBar 最大值
        seekBarTime.setMax(entries.size() - 1);
    }
    
    private void setupSeekBar() {
        seekBarTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                textTimeInfo.setText("时间点：" + progress);
                
                if (fromUser) {
                    // 用户手动拖动，更新图表显示
                    // TODO: 显示对应时间点的波形
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void startPlayback() {
        if (isPlaying) {
            return;
        }
        
        isPlaying = true;
        currentPosition = 0;
        
        playRunnable = () -> {
            if (currentPosition < seekBarTime.getMax()) {
                currentPosition++;
                seekBarTime.setProgress(currentPosition);
                handler.postDelayed(playRunnable, 50);  // 每 50ms 更新一帧
            } else {
                stopPlayback();
            }
        };
        
        handler.post(playRunnable);
        Toast.makeText(this, "开始播放", Toast.LENGTH_SHORT).show();
    }
    
    private void pausePlayback() {
        isPlaying = false;
        if (playRunnable != null) {
            handler.removeCallbacks(playRunnable);
        }
        Toast.makeText(this, "暂停播放", Toast.LENGTH_SHORT).show();
    }
    
    private void stopPlayback() {
        pausePlayback();
        currentPosition = 0;
        seekBarTime.setProgress(0);
        Toast.makeText(this, "停止播放", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }
}
