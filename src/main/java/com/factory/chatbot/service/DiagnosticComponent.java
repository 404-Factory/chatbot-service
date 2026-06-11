package com.factory.chatbot.service;

import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticComponent {

    private final ApplicationContext ctx;

    public DiagnosticComponent(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onStart() {
        System.out.println("=== DIAGNOSTIC START ===");
        String[] beans = ctx.getBeanDefinitionNames();
        System.out.println("Total beans: " + beans.length);
        
        boolean hasCommonKafkaConsumer = ctx.containsBean("commonKafkaConsumer");
        boolean hasEventDispatcher = ctx.containsBean("eventDispatcher");
        boolean hasAnalysisRequestedHandler = ctx.containsBean("analysisRequestedHandler");
        
        System.out.println("Has commonKafkaConsumer bean: " + hasCommonKafkaConsumer);
        System.out.println("Has eventDispatcher bean: " + hasEventDispatcher);
        System.out.println("Has analysisRequestedHandler bean: " + hasAnalysisRequestedHandler);
        
        try {
            System.out.println("--- BEAN SEARCH ---");
            for (String name : beans) {
                String lower = name.toLowerCase();
                if (lower.contains("sigma") || lower.contains("kafka") || lower.contains("auto") || lower.contains("properties")) {
                    System.out.println("  " + name + " -> " + ctx.getBean(name).getClass().getName());
                }
            }
            System.out.println("-------------------");
        } catch (Exception e) {
            System.out.println("Error searching beans: " + e.getMessage());
        }

        if (hasAnalysisRequestedHandler) {
            Object handler = ctx.getBean("analysisRequestedHandler");
            System.out.println("analysisRequestedHandler class: " + handler.getClass().getName());
        }
        
        System.out.println("=== DIAGNOSTIC END ===");
    }
}
