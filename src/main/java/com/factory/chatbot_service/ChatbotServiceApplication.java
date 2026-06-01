package com.factory.chatbot_service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.factory.chatbot_service.repository")
public class ChatbotServiceApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(ChatbotServiceApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        Path dotenv = Path.of(".env");
        if (!Files.isRegularFile(dotenv)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(dotenv)) {
                loadDotEnvLine(line);
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadDotEnvLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }

        int separatorIndex = trimmed.indexOf('=');
        if (separatorIndex <= 0) {
            return;
        }

        String key = trimmed.substring(0, separatorIndex).trim();
        String value = stripQuotes(trimmed.substring(separatorIndex + 1).trim());
        if (key.isEmpty() || System.getenv(key) != null || System.getProperty(key) != null) {
            return;
        }

        System.setProperty(key, value);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}