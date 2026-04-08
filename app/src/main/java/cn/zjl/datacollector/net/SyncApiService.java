package cn.zjl.datacollector.net;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * 数据同步 API 接口
 */
public interface SyncApiService {
    @POST("api/sync/upload")
    Call<SyncResponse> uploadData(@Body SyncRequest request);
}
