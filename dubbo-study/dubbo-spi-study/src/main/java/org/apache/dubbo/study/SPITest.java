package org.apache.dubbo.study;


import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.study.api.Car;

public class SPITest {


    public static void main(String[] args) {

        ExtensionLoader<Car> loader = ExtensionLoader.getExtensionLoader(Car.class);
        Car car = loader.getExtension("red");
        System.out.println(car.getColor(null));

    }
}
