package cn.zjl.datacollector.sync;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import cn.zjl.datacollector.net.api.AuthLoginPayload;
import cn.zjl.datacollector.net.api.RetrofitClient;
import cn.zjl.datacollector.net.api.SampleFullPayload;
import cn.zjl.datacollector.net.api.SyncApiService;
import cn.zjl.datacollector.net.api.SyncRequest;
import cn.zjl.datacollector.net.api.SyncResponse;
import cn.zjl.datacollector.sync.auth.SyncAuthManager;
import cn.zjl.datacollector.sync.inspect.BackendSyncInspector;
import cn.zjl.datacollector.util.AppSettings;
import retrofit2.Response;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * 面向真实后端的手工联调测试。
 *
 * 运行时通过 instrumentation args 注入参数，避免把账号密码写进仓库：
 * syncBaseUrl, syncUsername, syncPassword, syncRemoteProjectId,
 * syncEngineeringCode, syncLineCode, syncPointNumber, syncPointCode
 */
@RunWith(AndroidJUnit4.class)
public class LiveSyncUploadInstrumentedTest {

    private static final String ARG_BASE_URL = "syncBaseUrl";
    private static final String ARG_USERNAME = "syncUsername";
    private static final String ARG_PASSWORD = "syncPassword";
    private static final String ARG_REMOTE_PROJECT_ID = "syncRemoteProjectId";
    private static final String ARG_ENGINEERING_CODE = "syncEngineeringCode";
    private static final String ARG_LINE_CODE = "syncLineCode";
    private static final String ARG_POINT_NUMBER = "syncPointNumber";
    private static final String ARG_POINT_CODE = "syncPointCode";

    private Context context;
    private Bundle arguments;

    private String originalBaseUrl;
    private String originalUsername;
    private String originalPassword;
    private String originalToken;
    private String originalDeviceId;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        arguments = InstrumentationRegistry.getArguments();

        originalBaseUrl = AppSettings.getSyncBaseUrl(context);
        originalUsername = AppSettings.getSyncUsername(context);
        originalPassword = AppSettings.getSyncPassword(context);
        originalToken = AppSettings.getSyncToken(context);
        originalDeviceId = AppSettings.getSyncDeviceId(context);

        AppSettings.setSyncBaseUrl(context, requireArgument(ARG_BASE_URL, AppSettings.getSyncBaseUrl(context)));
        AppSettings.setSyncUsername(context, requireArgument(ARG_USERNAME, ""));
        AppSettings.setSyncPassword(context, requireArgument(ARG_PASSWORD, ""));
        AppSettings.clearSyncToken(context);
        AppSettings.setSyncDeviceId(context, "ANDROID-TEST-UPLOADER");
    }

    @After
    public void tearDown() {
        AppSettings.setSyncBaseUrl(context, originalBaseUrl);
        AppSettings.setSyncUsername(context, originalUsername);
        AppSettings.setSyncPassword(context, originalPassword);
        if (originalToken == null || originalToken.trim().isEmpty()) {
            AppSettings.clearSyncToken(context);
        } else {
            AppSettings.setSyncToken(context, originalToken);
        }
        AppSettings.setSyncDeviceId(context, originalDeviceId);
    }

    @Test
    public void login_shouldReturnToken() throws Exception {
        SyncAuthManager authManager = new SyncAuthManager(context);
        SyncAuthManager.LoginResult result = authManager.loginSync();

        assertTrue("登录失败: " + result.message, result.success);
        assertFalse("Token 为空", result.token == null || result.token.trim().isEmpty());

        BackendSyncInspector inspector = new BackendSyncInspector(context);
        AuthLoginPayload.UserPayload user = inspector.getCurrentUserSync();
        assertNotNull("当前用户信息为空", user);
        assertTrue("账号不具备 Android 上传权限: " + user.username,
                Boolean.TRUE.equals(user.canUploadAndroid));
    }

    @Test
    public void uploadSinglePoint_shouldSucceedWhenRemoteProjectIdProvided() throws Exception {
        String remoteProjectIdValue = requireArgument(ARG_REMOTE_PROJECT_ID, "");
        assumeTrue("未提供 syncRemoteProjectId，跳过真实上传测试",
                remoteProjectIdValue != null && !remoteProjectIdValue.trim().isEmpty());

        SyncAuthManager authManager = new SyncAuthManager(context);
        SyncAuthManager.LoginResult loginResult = authManager.loginSync();
        assertTrue("登录失败，无法上传: " + loginResult.message, loginResult.success);

        SyncApiService apiService = RetrofitClient.getSyncApiService(context);
        SyncRequest request = buildSampleRequest(Long.parseLong(remoteProjectIdValue.trim()));
        Response<SyncResponse> response = apiService.uploadData(request).execute();

        assertTrue("HTTP 上传失败，code=" + response.code(), response.isSuccessful());
        SyncResponse body = response.body();
        assertNotNull("响应体为空", body);
        assertTrue("业务返回失败: code=" + body.code + ", message=" + body.message,
                body.code == 200 || body.code == 0);

        Long sampleId = body.data != null && body.data.sampleIds != null && !body.data.sampleIds.isEmpty()
                ? body.data.sampleIds.get(0)
                : null;
        if (sampleId != null) {
            BackendSyncInspector inspector = new BackendSyncInspector(context);
            SampleFullPayload sample = inspector.getSampleFullSync(sampleId);
            assertNotNull("样本详情为空，sampleId=" + sampleId, sample);
        }
    }

    private SyncRequest buildSampleRequest(long remoteProjectId) {
        long now = System.currentTimeMillis();

        SyncRequest request = new SyncRequest();
        request.batchNo = "ANDROID-IT-" + now;
        request.deviceId = AppSettings.getSyncDeviceId(context);
        request.deviceType = "ANDROID";
        request.projectId = remoteProjectId;
        request.engineeringCode = requireArgument(ARG_ENGINEERING_CODE, "ANDROID-TEST");
        request.lineCode = requireArgument(ARG_LINE_CODE, "1");

        SyncRequest.PointPayload point = new SyncRequest.PointPayload();
        point.pointCode = requireArgument(ARG_POINT_CODE, "P" + (now % 100000));
        point.pointNumber = Integer.parseInt(requireArgument(ARG_POINT_NUMBER, "1"));
        point.latitude = 30.223456;
        point.longitude = 120.223456;
        point.altitude = 18.6;
        point.elevation = 18.6;

        SyncRequest.ParameterPayload parameter = new SyncRequest.ParameterPayload();
        parameter.sendCurrent = 1.2;
        parameter.samplingFrequency = 500;
        parameter.collectionCount = 1;
        parameter.samplingTime = 10.0;
        parameter.poleDistance = 6.0;
        parameter.coilDirection = "EAST";
        point.parameter = parameter;

        SyncRequest.MonitorPayload monitor = new SyncRequest.MonitorPayload();
        monitor.batteryVoltage = 82.5;
        monitor.current = 0.6;
        monitor.temperature = 29.1;
        monitor.signalStrength = 78;
        monitor.gpsAccuracy = 96.5;
        monitor.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(now));
        point.monitor = monitor;

        point.waveforms.add(buildWaveform(1, new Double[]{0d, 1d, 2d}, new Double[]{0.2, 0.3, 0.1}));
        point.waveforms.add(buildWaveform(2, new Double[]{0d, 1d, 2d}, new Double[]{0.0, -0.1, 0.0}));
        point.waveforms.add(buildWaveform(3, new Double[]{0d, 1d, 2d}, new Double[]{1.0, 1.5, 1.2}));

        request.points.add(point);
        return request;
    }

    private SyncRequest.WaveformPayload buildWaveform(int type, Double[] timeSeries, Double[] voltageSeries) {
        SyncRequest.WaveformPayload waveform = new SyncRequest.WaveformPayload();
        waveform.waveformType = type;
        waveform.timeSeries.addAll(Arrays.asList(timeSeries));
        waveform.voltageSeries.addAll(Arrays.asList(voltageSeries));
        return waveform;
    }

    private String requireArgument(String key, String fallback) {
        String value = arguments != null ? arguments.getString(key) : null;
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
