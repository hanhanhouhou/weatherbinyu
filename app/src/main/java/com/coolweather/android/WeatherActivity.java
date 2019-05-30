package com.coolweather.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.Address;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.bumptech.glide.Glide;
import com.coolweather.android.gson.AQI;
import com.coolweather.android.gson.Weather;
import com.coolweather.android.service.AutoUpdateService;
import com.coolweather.android.util.HttpUtil;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static com.coolweather.android.util.Utility.handleAQIResponse;
import static com.coolweather.android.util.Utility.handleWeatherResponse;

public class WeatherActivity extends AppCompatActivity {

    public static final String KEY_CITY = "KEY_CITY";
    public static final String KEY_LOCATION = "KEY_LOCATION";
    public static final String KEY_AUTO_LOCATE = "KEY_AUTO_LOCATE";
    public SwipeRefreshLayout swipeRefresh;
    public DrawerLayout drawerLayout;
    private ScrollView weatherLayout;
    private TextView titleCity;
    private TextView titleUpdateTime;
    private TextView degreeText;
    private TextView weatherInfoText;
    private LinearLayout forecastLayout;
    private TextView aqiText;
    private TextView pm25Text;
    private TextView comfortText;
    private TextView carWashText;
    private TextView sportText;
    private ImageView bingPicImg;
    // for querying aqi
    private String currentCity;
    // for querying other info
    private String currentLocation;
    // modify in main!!!
    private Call currentWeatherCall;
    // modify in main!!!
    private Call currentAqiCall;
    private boolean located = false;
    private LocationClient locationClient = null;

    private final BDAbstractLocationListener bdLocationListener = new BDAbstractLocationListener() {

        @Override
        public void onReceiveLocation(final BDLocation bdLocation) {
            Address address = bdLocation.getAddress();
            locationClient.stop();
            if (address == null || !bdLocation.hasAddr()) {
                titleUpdateTime.setText("");
                Toast.makeText(WeatherActivity.this, "获取位置失败", Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
                if (currentCity == null) {
                    // Beijing default
                    currentCity = "北京";
                    currentLocation = "北京";
                    requestWeather();
                }
                return;
            }
            located = true;
            Intent intent = new Intent(WeatherActivity.this, WeatherActivity.class);
            intent.putExtra(KEY_CITY, address.city);
            intent.putExtra(KEY_LOCATION, address.address == null ? address.city : address.district);
            startActivity(intent);
        }

        @Override
        public void onLocDiagnosticMessage(int i, int i1, String s) {
            titleUpdateTime.setText("");
            swipeRefresh.setRefreshing(false);
        }
    };

    private void handleNewAddressLocate(String city, String location) {
        currentCity = city;
        currentLocation = location;
        requestWeather();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (drawerLayout != null && drawerLayout.isDrawerOpen(Gravity.START)) {
            drawerLayout.closeDrawer(Gravity.START);
        }
        // single top
        // when start again
        String city = intent.getStringExtra(KEY_CITY);
        String location = intent.getStringExtra(KEY_LOCATION);
        if (city != null && location != null) {
            handleNewAddressLocate(city, location);
        }
    }

    @SuppressLint("ResourceAsColor")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        setContentView(R.layout.activity_weather);
        //初始化各控件
        swipeRefresh = findViewById(R.id.swipe_refresh);
        weatherLayout = findViewById(R.id.weather_layout);
        titleCity = findViewById(R.id.title_city);
        titleUpdateTime = findViewById(R.id.title_update_time);
        degreeText = findViewById(R.id.degree_text);
        weatherInfoText = findViewById(R.id.weather_info_text);
        forecastLayout = findViewById(R.id.forecast_layout);
        aqiText = findViewById(R.id.aqi_text);
        pm25Text = findViewById(R.id.pm25_text);
        comfortText = findViewById(R.id.comfort_text);
        carWashText = findViewById(R.id.car_wash_text);
        sportText = findViewById(R.id.sport_text);
        bingPicImg = findViewById(R.id.bing_pic_img);
        drawerLayout = findViewById(R.id.drawer_layout);
        Button navButton = findViewById(R.id.nav_button);
        swipeRefresh.setColorSchemeColors(R.color.colorPrimary);

        Intent intent = getIntent();
        String city = intent.getStringExtra(KEY_CITY);
        String queryWeather = intent.getStringExtra(KEY_LOCATION);
        boolean isAutoLocate = intent.getBooleanExtra(KEY_AUTO_LOCATE, true) &&
                (city == null || queryWeather == null);

        final View view = findViewById(R.id.best_livable_citis__text);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (isAutoLocate) {
            view.setVisibility(View.VISIBLE);
            view.setOnClickListener(v -> startActivity(new Intent(WeatherActivity.this, BestLivableCitiesActivity.class)));
            located = false;
            setupLocationClient();
            titleCity.setOnClickListener(v -> new AlertDialog.Builder(WeatherActivity.this)
                    .setMessage("重新定位?")
                    .setPositiveButton("是", (dialog, which) -> startLocate())
                    .setNegativeButton("否", null)
                    .show());
            navButton.setOnClickListener(v -> drawerLayout.openDrawer(Gravity.START));
            String weatherString = prefs.getString("weather", null);
            String aqiString = prefs.getString("aqi", null);

            if (weatherString != null && aqiString != null && !located) {
                try {
                    //有缓存时先显示缓存再请求
                    Weather weather = handleWeatherResponse(weatherString);
                    Weather.HeWeather6Bean.BasicBean basicX = weather.getHeWeather6().get(0).getBasicX();
                    currentCity = basicX.getParent_city();
                    showWeatherInfo(weather);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            view.setVisibility(View.GONE);
            navButton.setVisibility(View.GONE);
        }

        if (!isAutoLocate) {
            onNewIntent(intent);
        }


        swipeRefresh.setOnRefreshListener(this::requestWeather);

        //读取缓存在SharedPreference的pic数据,
        String bingPic = prefs.getString("bing_pic", null);
        if (bingPic != null) {
            Glide.with(this).load(bingPic).into(bingPicImg);
        } else {
            loadBingPic();
        }

    }

    private void setupLocationClient() {
        locationClient = new LocationClient(getApplicationContext());
        locationClient.registerLocationListener(bdLocationListener);
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setOpenGps(true);
        option.setIsNeedLocationDescribe(true);
        option.SetIgnoreCacheException(false);
        option.setIsNeedAddress(true);
        option.setIsNeedAltitude(true);
        locationClient.setLocOption(option);
        locationClient.start();
    }

    // main thread !!
    @MainThread
    private void startLocate() {
        boolean success = true;
        if (locationClient.isStarted()) {
            if (locationClient.requestLocation() != 0) {
                success = false;
            }
        } else {
            locationClient.start();
        }

        if (success) {
            titleUpdateTime.setText("");
            swipeRefresh.setRefreshing(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (locationClient != null) {
            // avoid memory leak
            locationClient.unRegisterLocationListener(bdLocationListener);
            locationClient.stop();
        }
        super.onDestroy();
    }

    /**
     * 加载必应每日一图
     */
    private void loadBingPic() {
        String requestBingPic = "http://guolin.tech/api/bing_pic";
        HttpUtil.sendOkHttpRequest(requestBingPic, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String bingpic = response.body().string();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(WeatherActivity.this).edit();
                editor.putString("bing_pic", bingpic);
                editor.apply();
                runOnUiThread(() -> Glide.with(WeatherActivity.this).load(bingpic).into(bingPicImg));
            }
        });
    }

    /**
     * 根据天气id请求城市天气信息
     * location可以是id也可以是
     */
    public void requestWeather() {
        // cancel last request
        if (currentAqiCall != null) {
            currentAqiCall.cancel();
        }
        if (currentWeatherCall != null) {
            currentWeatherCall.cancel();
        }

        final String weatherUrl = "https://free-api.heweather.com/s6/weather?location=" + currentLocation + "&key=" + BuildConfig.HE_FENG_KEY;
        final String aqiUrl = "https://free-api.heweather.com/s6/air/now?location=" + currentCity + "&key=" + BuildConfig.HE_FENG_KEY;

        currentWeatherCall = HttpUtil.sendOkHttpRequest(weatherUrl, new Callback() {

            @Override
            public void onFailure(@NonNull final Call call, @NonNull IOException e) {
                if (call.isCanceled() || call != currentWeatherCall) {
                    // ignore it
                    return;
                }
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentWeatherCall != call) {
                            // ignore it
                            return;
                        }
                        Toast.makeText(WeatherActivity.this, "获取天气信息失败onFailure", Toast.LENGTH_LONG).show();
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onResponse(@NonNull final Call call, @NonNull Response response) throws IOException {
                final String responseText = response.body().string();
                final Weather weather = handleWeatherResponse(responseText);
                runOnUiThread(() -> {
                    if (currentWeatherCall != call) {
                        // ignore it
                        return;
                    }
                    if ((weather != null) && "ok".equals(weather.getHeWeather6().get(0).getStatusX())) {
                        if (locationClient != null) {
                            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(WeatherActivity.this).edit();
                            editor.putString("weather", responseText);
                            editor.apply();
                        }
                        showWeatherInfo(weather);

                    } else {
                        Toast.makeText(WeatherActivity.this, responseText, Toast.LENGTH_SHORT).show();

                    }
                    swipeRefresh.setRefreshing(false);
                    currentWeatherCall = null;
                });

            }
        });

        currentAqiCall = HttpUtil.sendOkHttpRequest(aqiUrl, new Callback() {
            @Override
            public void onFailure(@NonNull final Call call, @NonNull IOException e) {
                if (call.isCanceled()) {
                    // ignore it
                    return;
                }
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (call != currentAqiCall) {
                        // ignore it
                        return;
                    }
                    Toast.makeText(WeatherActivity.this, "获取天气信息失败onFailure", Toast.LENGTH_LONG).show();
                    swipeRefresh.setRefreshing(false);
                });
            }

            @Override
            public void onResponse(@NonNull final Call call, @NonNull Response response) throws IOException {
                final String responseText = response.body().string();
                final AQI aqi = handleAQIResponse(responseText);
                runOnUiThread(() -> {
                    if (currentAqiCall != call) {
                        // ignore it
                        return;
                    }
                    if ((aqi != null) && "ok".equals(aqi.getHeWeather6().get(0).getStatus())) {
                        if (locationClient != null) {
                            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(WeatherActivity.this).edit();
                            editor.putString("aqi", responseText);
                            editor.apply();
                        }
                        showAQIInfo(aqi);
                    } else {
                        Toast.makeText(WeatherActivity.this, responseText, Toast.LENGTH_SHORT).show();

                    }
                    swipeRefresh.setRefreshing(false);
                    currentAqiCall = null;
                });
            }
        });
    }

    private void showAQIInfo(AQI aqi) {
        if (aqi != null) {
            aqiText.setText(aqi.getHeWeather6().get(0).getAir_now_city().getAqi());
            pm25Text.setText(aqi.getHeWeather6().get(0).getAir_now_city().getPm25());
        }
    }


    private void showWeatherInfo(Weather weather) {

        String cityName = weather.getHeWeather6().get(0).getBasicX().getLocation();
        String updateTime = weather.getHeWeather6().get(0).getUpdate().getLoc();
        String degree = weather.getHeWeather6().get(0).getNowX().getTmp() + "℃";
        String weatherInfo = weather.getHeWeather6().get(0).getNowX().getCond_txt();
        titleCity.setText(cityName);
        titleUpdateTime.setText(updateTime);
        degreeText.setText(degree);
        weatherInfoText.setText(weatherInfo);
        forecastLayout.removeAllViews();

        for (int i = 0; i < 3; i++) {
            View view = LayoutInflater.from(this).inflate(R.layout.forecast_item, forecastLayout, false);
            TextView dataText = view.findViewById(R.id.data_text);
            TextView infoText = view.findViewById(R.id.info_text);
            TextView maxText = view.findViewById(R.id.max_text);
            TextView minText = view.findViewById(R.id.min_text);

            dataText.setText(weather.getHeWeather6().get(0).getDaily_forecast().get(i).getDate());
            infoText.setText(weather.getHeWeather6().get(0).getDaily_forecast().get(i).getCond_txt_n());
            maxText.setText(weather.getHeWeather6().get(0).getDaily_forecast().get(i).getTmp_max());
            minText.setText(weather.getHeWeather6().get(0).getDaily_forecast().get(i).getTmp_min());
            forecastLayout.addView(view);
        }


        comfortText.setText(("舒适度：" + weather.getHeWeather6().get(0).getLifestyle().get(0).getTxt()));
        carWashText.setText(("洗车指数：" + weather.getHeWeather6().get(0).getLifestyle().get(6).getTxt()));
        sportText.setText(("运动指数：" + weather.getHeWeather6().get(0).getLifestyle().get(3).getTxt()));

        weatherLayout.setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, AutoUpdateService.class);
        startService(intent);
    }
}
