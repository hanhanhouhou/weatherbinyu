package com.coolweather.android;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.coolweather.android.util.CityLivableExp;
import com.coolweather.android.util.CityLivableExpFetcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Liu Yuchuan on 2019/5/29.
 */
public class BestLivableCitiesFragment extends Fragment implements AdapterView.OnItemClickListener {
    private ListView listView;
    private ArrayAdapter<CityLivableExp> adapter;
    private List<CityLivableExp> dataList = new ArrayList<>();
    private CityLivableExpFetcher cityLivableExpFetcher = CityLivableExpFetcher.instance();
    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            fillListData();
        }
    };
    private Handler handler = new Handler();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.choose_area, container, false);
        TextView titleText = view.findViewById(R.id.title_text);
        titleText.setText("一线城市宜居度排名");
        view.findViewById(R.id.back_button).setVisibility(View.GONE);
        view.findViewById(R.id.des).setVisibility(View.VISIBLE);
        listView = view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);
        listView.setOnItemClickListener(this);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (cityLivableExpFetcher.isReady()) {
            fillListData();
        } else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    cityLivableExpFetcher.waitingForReady();
                    handler.removeCallbacks(r);
                    handler.post(r);
                }
            }).start();
        }
    }

    private void fillListData() {
        dataList.clear();
        dataList.addAll(cityLivableExpFetcher.getCityLivableExpList());
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(r);
        super.onDestroy();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CityLivableExp cityLivableExp = dataList.get(position);
        Intent intent = new Intent(getActivity(), WeatherActivity.class);
        intent.putExtra(WeatherActivity.KEY_LOCATION, cityLivableExp.name);
        intent.putExtra(WeatherActivity.KEY_CITY, cityLivableExp.name);
        intent.putExtra(WeatherActivity.KEY_AUTO_LOCATE, false);
        startActivity(intent);
    }
}
