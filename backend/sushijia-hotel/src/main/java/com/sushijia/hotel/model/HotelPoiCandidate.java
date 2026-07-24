package com.sushijia.hotel.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HotelPoiCandidate {
    private String provider;
    private String poiId;
    private String name;
    private String address;
    private String province;
    private String city;
    private String district;
    private String adcode;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String typeCode;
    private String typeName;
    private String keytag;
    private String tel;
    private String businessArea;
    private String rating;
    private BigDecimal lowestPrice;
}
