package org.apache.dubbo.study;



import org.apache.dubbo.study.service.Car;

import java.util.Iterator;
import java.util.ServiceLoader;

public class carDemo {

    public static void main(String[] args) {

        ServiceLoader<Car> serviceLoader = ServiceLoader.load(Car.class);
        Iterator<Car> iterator = serviceLoader.iterator();
        //dubbo spi 比 java spi多了key的机制，还支持了AOP和IOC功能
        //dubbo中一个服务就是一个接口，一个接口就是一个URL （总线）
        while (iterator.hasNext()) {

            Car car = iterator.next();
            System.out.println(car.getColor());
        }

    }
}
