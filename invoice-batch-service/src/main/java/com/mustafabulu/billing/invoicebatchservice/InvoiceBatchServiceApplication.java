package com.mustafabulu.billing.invoicebatchservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mustafabulu.billing")
public class InvoiceBatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceBatchServiceApplication.class, args);
    }
}

