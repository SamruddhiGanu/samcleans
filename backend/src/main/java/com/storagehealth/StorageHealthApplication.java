package com.storagehealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Storage Health Ranker application.
 * Bootstraps the Spring Boot context, which includes JPA, REST, and SQLite configuration.
 */
@SpringBootApplication
public class StorageHealthApplication {
    public static void main(String[] args) {
        SpringApplication.run(StorageHealthApplication.class, args);
    }
}
