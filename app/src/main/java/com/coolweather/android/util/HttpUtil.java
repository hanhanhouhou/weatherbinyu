package com.coolweather.android.util;

import com.coolweather.android.BuildConfig;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * 封装okhttp的网络请求
 */
public class HttpUtil {
    private static final OkHttpClient CLIENT;

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
        CLIENT = builder.build();
        CLIENT.dispatcher().setMaxRequests(2);
    }

    public static Call sendOkHttpRequest(String address, okhttp3.Callback callback) {
        Request request = new Request.Builder().url(address).build();
        Call call = CLIENT.newCall(request);
        call.enqueue(callback);
        return call;
    }
}
