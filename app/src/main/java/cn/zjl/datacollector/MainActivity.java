package cn.zjl.datacollector;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import cn.zjl.datacollector.ui.ProjectListActivity;

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