package org.apache.dubbo.study.api;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.SPI;

@SPI
public interface Driver {

    void driverCar(URL url);
}
