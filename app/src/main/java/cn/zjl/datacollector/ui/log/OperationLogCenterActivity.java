// ui/log/OperationLogCenterActivity.java — 迁移后版本
package cn.zjl.datacollector.ui.log;

import android.os.Bundle;

import androidx.activity.ComponentActivity;


public class OperationLogCenterActivity extends ComponentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComposeWrappers.launchOperationLogCenter(this);
    }
}
