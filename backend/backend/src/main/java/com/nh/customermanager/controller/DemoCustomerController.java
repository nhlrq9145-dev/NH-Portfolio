package com.nh.customermanager.controller;

import com.nh.customermanager.dto.DemoCustomerResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/demo/customers")
public class DemoCustomerController {

    private static final List<DemoCustomerResponse> DEMO_CUSTOMERS =
            List.of(
                    new DemoCustomerResponse(
                            "演示客户·青禾咖啡馆",
                            "餐饮",
                            "ACTIVE"
                    ),
                    new DemoCustomerResponse(
                            "演示客户·星云设计工作室",
                            "设计服务",
                            "POTENTIAL"
                    ),
                    new DemoCustomerResponse(
                            "演示客户·远山手作商店",
                            "零售",
                            "POTENTIAL"
                    )
            );

    @GetMapping
    public List<DemoCustomerResponse> findAll() {
        return DEMO_CUSTOMERS;
    }
}
