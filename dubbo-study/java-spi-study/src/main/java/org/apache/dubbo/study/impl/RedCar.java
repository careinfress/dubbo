package org.apache.dubbo.study.impl;


import org.apache.dubbo.study.service.Car;

public class RedCar implements Car {
    @Override
    public String getColor() {
        System.out.println("red");
        return "red";
    }
}
