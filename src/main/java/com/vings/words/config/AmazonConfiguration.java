package com.vings.words.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmazonConfiguration {

    @Bean
    public AmazonS3 s3Client() {
        return AmazonS3ClientBuilder.standard().build();
    }
}
