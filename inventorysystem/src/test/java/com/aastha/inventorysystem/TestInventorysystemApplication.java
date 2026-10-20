package com.aastha.inventorysystem;

import org.springframework.boot.SpringApplication;

public class TestInventorysystemApplication {

    public static void main(String[] args) {
        SpringApplication.from(InventorysystemApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
