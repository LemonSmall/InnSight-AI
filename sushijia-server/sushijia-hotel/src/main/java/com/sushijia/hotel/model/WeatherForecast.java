package com.sushijia.hotel.model;

import lombok.Data;

@Data
public class WeatherForecast {
    private String date;
    private String week;
    private String dayWeather;
    private String nightWeather;
    private String dayTemp;
    private String nightTemp;
    private String dayWind;
    private String nightWind;
    private String dayPower;
    private String nightPower;
}
