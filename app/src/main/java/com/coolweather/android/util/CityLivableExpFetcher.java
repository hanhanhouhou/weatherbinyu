package com.coolweather.android.util;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.coolweather.android.BuildConfig;
import com.coolweather.android.gson.Weather;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

import static com.coolweather.android.util.Utility.handleWeatherResponse;

/**
 * Created by Liu Yuchuan on 2019/5/29.
 */
public class CityLivableExpFetcher extends Thread {
    private static final String[] cityList = {
            "北京",
            "上海",
            "广州",
            "深圳",
            "杭州",
            "重庆",
            "成都",
            "武汉",
            "西安"
    };
    private static CityLivableExpFetcher INSTANCE;
    private final OkHttpClient client = new OkHttpClient();
    private final List<CityLivableExp> cityLivableExpList = new ArrayList<>();
    private final Map<String, CityLivableExp> cityLivableExpMap = new HashMap<>();
    private volatile boolean ready = false;
    private Lock lock = new ReentrantLock();
    private volatile CountDownLatch waitingResultCountDownLatch;
    private CountDownLatch callerWaitingCountDownLatch;

    private CityLivableExpFetcher() {
        super("CityLivableExpFetcher");
        start();
    }

    public static CityLivableExpFetcher instance() {
        if (INSTANCE == null) {
            synchronized (CityLivableExpFetcher.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CityLivableExpFetcher();
                }
            }
        }
        return INSTANCE;
    }

    public static void go() {
        instance();
    }

    @Override
    public void run() {
        callerWaitingCountDownLatch = new CountDownLatch(1);
        boolean success = false;
        while (!success) {
            success = fetch();
            Log.d("success", success + " " + cityLivableExpMap.values().size());
        }
        callerWaitingCountDownLatch.countDown();
        ready = true;
    }

    private boolean fetch() {
        if (ready) {
            return false;
        }
        Set<String> cities = new HashSet<>();
        for (String s : cityList) {
            if (cityLivableExpMap.get(s) == null) {
                cities.add(s);
            }
        }
        waitingResultCountDownLatch = new CountDownLatch(cities.size());
        for (String s : cities) {
            fetchAndCalculate(s);
        }

        if (waitingResultCountDownLatch != null) {
            while (waitingResultCountDownLatch.getCount() > 0) {
                try {
                    waitingResultCountDownLatch.await();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }

        for (String s : cities) {
            if (cityLivableExpMap.get(s) == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return false;
            }
        }
        cityLivableExpList.clear();
        cityLivableExpList.addAll(cityLivableExpMap.values());
        Collections.sort(cityLivableExpList);
        return true;
    }

    private void fetchAndCalculate(final String city) {
        String url = "https://free-api.heweather.com/s6/weather?location=" + city + "&key=" + BuildConfig.HE_FENG_KEY;
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                waitingResultCountDownLatch.countDown();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    final String responseText = response.body().string();
                    final Weather weather = handleWeatherResponse(responseText);
                    if ((weather != null) && "ok".equals(weather.getHeWeather6().get(0).getStatusX())) {
                        CityLivableExp cityLivableExp = null;
                        List<Weather.HeWeather6Bean.DailyForecastBean> daily_forecast = weather.getHeWeather6().get(0).getDaily_forecast();
                        for (Weather.HeWeather6Bean.DailyForecastBean dailyForecastBean : daily_forecast) {
                            try {
                                double hum = Double.parseDouble(dailyForecastBean.getHum());
                                double tmpMax = Double.parseDouble(dailyForecastBean.getTmp_max());
                                double tmpMin = Double.parseDouble(dailyForecastBean.getTmp_min());
                                double t = (tmpMax + tmpMin) / 2;
                                double tf = t * 9 / 5 + 2;
                                double exp = tf - 0.55 * (1 - hum / 100) * (tf - 58);
                                cityLivableExp = new CityLivableExp(city, exp);
                                break;
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                        if (cityLivableExp == null) {
                            return;
                        }
                        try {
                            lock.lock();
                            cityLivableExpMap.put(city, cityLivableExp);
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (waitingResultCountDownLatch != null) {
                        waitingResultCountDownLatch.countDown();
                    }
                }
            }
        });
    }

    // block util ready
    @WorkerThread
    public void waitingForReady() {
        if (callerWaitingCountDownLatch != null) {
            while (callerWaitingCountDownLatch.getCount() > 0) {
                try {
                    callerWaitingCountDownLatch.await();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
    }

    public List<CityLivableExp> getCityLivableExpList() {
        // return copy of it
        return new ArrayList<>(cityLivableExpList);
    }

    public boolean isReady() {
        return ready;
    }
}
