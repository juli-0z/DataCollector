package cn.zjl.datacollector.ui.diagnostic;

import android.os.Bundle;

import androidx.activity.ComponentActivity;
import cn.zjl.datacollector.ui.log.ComposeWrappers;

/**
 * 设备联调诊断界面——已迁移至 Jetpack Compose。
 */
public class DeviceDiagnosticActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ComposeWrappers.launchDeviceDiagnostic(this);
    }
}
