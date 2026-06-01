package com.factory.chatbot_service.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;

@Configuration
public class AwsBedrockConfig {

    private AwsCredentialsProvider getCredentialsProvider() {
        String accessKey = System.getProperty("AWS_ACCESS_KEY_ID");
        String secretKey = System.getProperty("AWS_SECRET_ACCESS_KEY");
        
        if (accessKey == null || accessKey.trim().isEmpty()) {
            accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        }

        if (accessKey != null && !accessKey.trim().isEmpty() && 
            secretKey != null && !secretKey.trim().isEmpty()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean(name = "recipeBedrockAgentClient")
    public BedrockAgentRuntimeAsyncClient recipeBedrockAgentClient(
            @Value("${chatbot.aws.region}") String region,
            @Value("${chatbot.bedrock.api-call-timeout-seconds:180}") long apiCallTimeoutSeconds,
            @Value("${chatbot.bedrock.api-call-attempt-timeout-seconds:150}") long apiCallAttemptTimeoutSeconds,
            @Value("${chatbot.bedrock.http-read-timeout-seconds:150}") long httpReadTimeoutSeconds,
            @Value("${chatbot.bedrock.http-connection-timeout-seconds:10}") long httpConnectionTimeoutSeconds
    ) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(httpConnectionTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(httpReadTimeoutSeconds))
                .writeTimeout(Duration.ofSeconds(httpReadTimeoutSeconds))
                .build();

        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(apiCallTimeoutSeconds))
                .apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeoutSeconds))
                .build();

        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(getCredentialsProvider())
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean(name = "insightBedrockAgentClient")
    public BedrockAgentRuntimeAsyncClient insightBedrockAgentClient(
            @Value("${aws.region:ap-northeast-2}") String region
    ) {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .build();

        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(70))
                .apiCallAttemptTimeout(Duration.ofSeconds(60))
                .build();

        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(getCredentialsProvider())
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${chatbot.aws.region}") String region,
            @Value("${chatbot.bedrock.explanation-timeout-seconds:12}") long explanationTimeoutSeconds,
            @Value("${chatbot.bedrock.http-connection-timeout-seconds:5}") long httpConnectionTimeoutSeconds
    ) {
        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(explanationTimeoutSeconds))
                .apiCallAttemptTimeout(Duration.ofSeconds(explanationTimeoutSeconds))
                .build();

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(getCredentialsProvider())
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean
    public S3Client s3Client(
            @Value("${chatbot.aws.region}") String region
    ) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(getCredentialsProvider())
                .build();
    }
}