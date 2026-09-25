package com.truesight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Where the backend starts. Running this starts a web server on port 8080 (or $PORT), connects to the database,
 * creates any missing tables through Flyway, and makes every endpoint in this project available.
 */
@SpringBootApplication
public class TrueSightApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrueSightApplication.class, args);
    }
}
