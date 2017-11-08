package com.vings.words;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.pryshchepa.words")
public class WordsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WordsServerApplication.class, args);
    }
}
