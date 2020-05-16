package org.apache.dubbo.study.impl;


import org.apache.dubbo.study.service.Car;

public class BlackCar implements Car {
    @Override
    public String getColor() {
        System.out.println("black");
        return "black";
    }
}
