package com.factory.chatbot_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {
    @Value("${chatbot.aws.region}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(region))
            .build();
    }

    /// S3 Select 스트리밍 처리를 위한 비동기 Client
    @Bean
    public S3AsyncClient s3AsyncClient() {
        return S3AsyncClient.builder()
            .region(Region.of(region))
            .build();
    }
}
