package com.coolweather.android;

import com.coolweather.android.util.CityLivableExpFetcher;

import org.litepal.LitePalApplication;

/**
 * Created by Liu Yuchuan on 2019/5/29.
 */
public class App extends LitePalApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        CityLivableExpFetcher.go();
    }
}
