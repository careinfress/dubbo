package org.apache.dubbo.study.api;


import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Car {

    @Adaptive(value = "carType")
    String getColor(URL url);
}
