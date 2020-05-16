package org.apache.dubbo.study.impl;


import org.apache.dubbo.common.URL;
import org.apache.dubbo.study.api.Car;
import org.apache.dubbo.study.api.Driver;

public class Trcker implements Driver {

    private Car car;

    public void setCar(Car car) {
        this.car = car;
    }

    @Override
    public void driverCar(URL url) {
        System.out.println(car.getColor(url));
        System.out.println("driverCar");
    }
}
