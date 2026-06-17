package com.factory.chatbot.config;

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

    @Value("${aws.region:ap-northeast-2}")
    private String awsRegion;

    // Unified timeout settings for Bedrock Agent Runtime clients
    @Value("${chatbot.bedrock.api-call-timeout-seconds:180}")
    private long apiCallTimeout;

    @Value("${chatbot.bedrock.api-call-attempt-timeout-seconds:150}")
    private long apiCallAttemptTimeout;

    @Value("${chatbot.bedrock.http-read-timeout-seconds:150}")
    private long httpReadTimeout;

    @Value("${chatbot.bedrock.http-connection-timeout-seconds:10}")
    private long httpConnectionTimeout;

    // Timeout settings for bedrock runtime client
    @Value("${chatbot.bedrock.explanation-timeout-seconds:12}")
    private long explanationTimeout;

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
    public BedrockAgentRuntimeAsyncClient recipeBedrockAgentClient() {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(httpConnectionTimeout))
                .readTimeout(Duration.ofSeconds(httpReadTimeout))
                .writeTimeout(Duration.ofSeconds(httpReadTimeout))
                .build();

        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(apiCallTimeout))
                .apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeout))
                .build();

        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(getCredentialsProvider())
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean(name = "insightBedrockAgentClient")
    public BedrockAgentRuntimeAsyncClient insightBedrockAgentClient() {
        SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(httpConnectionTimeout))
                .readTimeout(Duration.ofSeconds(httpReadTimeout))
                .writeTimeout(Duration.ofSeconds(httpReadTimeout))
                .build();

        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(apiCallTimeout))
                .apiCallAttemptTimeout(Duration.ofSeconds(apiCallAttemptTimeout))
                .build();

        return BedrockAgentRuntimeAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(getCredentialsProvider())
                .httpClient(httpClient)
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        ClientOverrideConfiguration overrideConfiguration = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(explanationTimeout))
                .apiCallAttemptTimeout(Duration.ofSeconds(explanationTimeout))
                .build();

        return BedrockRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(getCredentialsProvider())
                .overrideConfiguration(overrideConfiguration)
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(getCredentialsProvider())
                .build();
    }
}