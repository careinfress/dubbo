package org.apache.dubbo.study.impl;

import org.apache.dubbo.study.api.Car;
import org.apache.dubbo.common.URL;

public class CarWapper implements Car {

    private Car car;

    public CarWapper(Car car) {
        this.car = car;
    }

    @Override
    public String getColor(URL url) {
        System.out.println("before");
        car.getColor(url);
        System.out.println("after");
        return "red wapper";
    }
}
