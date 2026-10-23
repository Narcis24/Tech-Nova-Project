package com.neueda.app.configs;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** "Now" for services that need it, so their tests can swap in Clock.fixed(...). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
