package cn.zjl.datacollector.net;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import cn.zjl.datacollector.util.AppSettings;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private RetrofitClient() {
    }

    public static SyncApiService getSyncApiService(Context context) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(AppSettings.getSyncBaseUrl(context))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(SyncApiService.class);
    }
}
