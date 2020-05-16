package org.apache.dubbo.study;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.study.api.Driver;

import java.util.HashMap;
import java.util.Map;

public class SPITest2 {
    public static void main(String[] args) {


        ExtensionLoader<Driver> loader = ExtensionLoader.getExtensionLoader(Driver.class);
        Driver driver = loader.getExtension("trcker");

        Map<String, String> map = new HashMap<>();
        map.put("carType", "red");
        URL url = new URL("","",0, map);

        driver.driverCar(url);
    }
}
