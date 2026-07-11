package cn.zjl.datacollector;

/**
 * 阅读提示：应用源码文件：参与 DataCollector 野外物探测点采集、存储、回放或同步流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import cn.zjl.datacollector.ui.project.ProjectListActivity;

/**
 * 应用主入口
 */
public class MainActivity extends AppCompatActivity {

    private Handler handler;
    private Runnable delayedRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        handler = new Handler(Looper.getMainLooper());
        delayedRunnable = () -> {
            if (!isFinishing()) {
                Intent intent = new Intent(MainActivity.this, ProjectListActivity.class);
                startActivity(intent);
                finish();
            }
        };
        
        handler.postDelayed(delayedRunnable, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && delayedRunnable != null) {
            handler.removeCallbacks(delayedRunnable);
        }
    }
}