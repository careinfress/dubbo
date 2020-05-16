package org.apache.dubbo.study.impl;



import org.apache.dubbo.common.URL;
import org.apache.dubbo.study.api.Car;

public class RedCar implements Car {
    @Override
    public String getColor(URL url) {
        System.out.println("red");
        return "red";
    }
}
