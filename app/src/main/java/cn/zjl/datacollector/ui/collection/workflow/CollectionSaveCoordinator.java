package cn.zjl.datacollector.ui.collection.workflow;

/**
 * 阅读提示：采集业务流程模块代码：负责连接、质检、保存和参数沿用等采集前后流程。
 * 本文件中的注释使用简体中文，便于按业务流程阅读代码；修改逻辑时请同步检查相关数据库、界面和同步链路。
 */

import android.location.Location;

import androidx.annotation.Nullable;

import cn.zjl.datacollector.data.entity.CollectionParameterEntity;
import cn.zjl.datacollector.data.entity.DeviceMonitorEntity;
import cn.zjl.datacollector.data.entity.MeasurementPointEntity;
import cn.zjl.datacollector.data.repository.DataRepository;

/**
 * 收敛测点保存时的写库请求参数组装，降低 ViewModel 中的数据落库细节占比。
 */
public class CollectionSaveCoordinator {

    public void saveCurrentPoint(DataRepository dataRepository,
                                 long lineId,
                                 float pointNumber,
                                 @Nullable CollectionParameterEntity parameters,
                                 @Nullable float[] recvTimeAxis,
                                 @Nullable float[] recvValues,
                                 @Nullable float[] sendValues,
                                 @Nullable float[] offValues,
                                 @Nullable DeviceMonitorEntity monitor,
                                 @Nullable Location location,
                                 boolean qualified,
                                 @Nullable String judgeNote,
                                 DataRepository.SaveCallback<MeasurementPointEntity> callback) {
        double latitude = location != null ? location.getLatitude() : 0d;
        double longitude = location != null ? location.getLongitude() : 0d;
        double altitude = location != null ? location.getAltitude() : 0d;
        if (monitor != null && location != null && location.hasAccuracy()) {
            monitor.setGpsAccuracy(location.getAccuracy());
        }

        dataRepository.saveCollectionSession(
                lineId,
                pointNumber,
                0,
                "",
                parameters,
                recvTimeAxis,
                recvValues,
                sendValues,
                offValues,
                monitor,
                latitude,
                longitude,
                altitude,
                qualified,
                judgeNote == null ? "" : judgeNote,
                callback);
    }
}
