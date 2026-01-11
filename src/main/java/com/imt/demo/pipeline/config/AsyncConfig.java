package com.imt.demo.pipeline.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Pipeline-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("Rejet de l'execution du pipeline : file d'attente pleine");
            throw new RuntimeException("Trop de pipelines en cours d'execution. Veuillez reessayer plus tard.");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Configuration de l'executor asynchrone pour les pipelines");
        log.info("Core pool size: {}", executor.getCorePoolSize());
        log.info("Max pool size: {}", executor.getMaxPoolSize());
        log.info("Queue capacity: {}", executor.getQueueCapacity());

        return executor;
    }
}
