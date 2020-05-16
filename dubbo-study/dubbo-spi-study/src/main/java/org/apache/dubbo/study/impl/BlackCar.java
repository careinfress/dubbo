package org.apache.dubbo.study.impl;


import org.apache.dubbo.study.api.Car;
import org.apache.dubbo.common.URL;


public class BlackCar implements Car {
    @Override
    public String getColor(URL uRl) {
        return "black";
    }
}
