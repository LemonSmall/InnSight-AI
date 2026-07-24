package com.sushijia.hotel.model;

import lombok.Data;

@Data
public class WeatherNow {
    private String province;
    private String city;
    private String adcode;
    private String weather;
    private String temperature;
    private String windDirection;
    private String windPower;
    private String humidity;
    private String reportTime;
}
