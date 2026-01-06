package com.imt.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration de l'exécution asynchrone pour les pipelines.
 * Permet d'exécuter plusieurs pipelines en parallèle.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Executor pour l'exécution asynchrone des pipelines.
     * Configure un pool de threads dédié aux pipelines.
     */
    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Nombre de threads principaux (toujours actifs)
        executor.setCorePoolSize(2);

        // Nombre maximum de threads
        executor.setMaxPoolSize(5);

        // Taille de la file d'attente
        executor.setQueueCapacity(50);

        // Préfixe du nom des threads
        executor.setThreadNamePrefix("Pipeline-");

        // Politique de rejet : log et rejette la tâche
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("❌ Rejet de l'exécution du pipeline : file d'attente pleine");
            throw new RuntimeException("Trop de pipelines en cours d'exécution. Veuillez réessayer plus tard.");
        });

        // Attendre la fin des tâches en cours lors de l'arrêt
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("🛠️ Configuration de l'executor asynchrone pour les pipelines");
        log.info("   - Core pool size: {}", executor.getCorePoolSize());
        log.info("   - Max pool size: {}", executor.getMaxPoolSize());
        log.info("   - Queue capacity: {}", executor.getQueueCapacity());

        return executor;
    }
}
