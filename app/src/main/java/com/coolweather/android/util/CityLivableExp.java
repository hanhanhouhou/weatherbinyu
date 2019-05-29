package com.coolweather.android.util;

import android.support.annotation.NonNull;

import java.util.Locale;

/**
 * Created by Liu Yuchuan on 2019/5/29.
 */
public class CityLivableExp implements Comparable<CityLivableExp> {
    public final String name;
    public final double exp;
    private final double sortExp;

    CityLivableExp(String name, double exp) {
        this.name = name;
        this.exp = exp;
        sortExp = Math.abs(exp - 65);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.CHINESE, "%s 舒适度指数：%.2f", name, exp);
    }

    @Override
    public int compareTo(CityLivableExp o) {
        return Double.compare(sortExp, o.sortExp);
    }
}
