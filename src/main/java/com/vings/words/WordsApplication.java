package com.vings.words;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.vings.words"})
public class WordsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordsApplication.class, args);
    }
}
