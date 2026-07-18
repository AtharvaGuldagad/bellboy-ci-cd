package com.bellboy.steward.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync //aloows async thread in the bg, and runs in parallel
public class AsyncConfig {
    
}