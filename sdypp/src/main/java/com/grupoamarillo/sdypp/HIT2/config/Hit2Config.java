package com.grupoamarillo.sdypp.HIT2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.grupoamarillo.sdypp.HIT2.concurrency.LamportClock;

@Configuration
public class Hit2Config {
    @Bean
    public LamportClock lamportClock(){
        return new LamportClock();
    }
}
