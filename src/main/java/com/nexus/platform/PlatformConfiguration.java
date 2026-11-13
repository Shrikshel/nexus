package com.nexus.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing the OS-specific Platform Abstraction Layer beans.
 */
@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {

    @Bean
    public SystemCollector systemCollector(PlatformDetector detector) {
        if (detector.isLinux()) {
            return new LinuxSystemCollector();
        }
        return new GenericSystemCollector();
    }
}
