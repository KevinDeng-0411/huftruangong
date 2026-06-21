package com.aicust;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
public class AiCustApplication {

    public static void main(String[] args) {
        // SSE 异步流式响应需要 SecurityContext 跨线程传递
        SecurityContextHolder.setStrategyName(
                SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SpringApplication.run(AiCustApplication.class, args);
    }

}
