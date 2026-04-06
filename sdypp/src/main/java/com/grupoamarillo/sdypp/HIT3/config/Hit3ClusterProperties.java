package com.grupoamarillo.sdypp.HIT3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "hit3.cluster")
public class Hit3ClusterProperties {
    private boolean enabled = false;
    private int nodeId = 1;
    private String selfHost = "localhost";
    private int selfPort = 8080;
    private String nodes = "1:localhost:8080";
    private long heartbeatIntervalMs = 1000;
    private long heartbeatTimeoutMs = 3000;
    private long electionTimeoutMs = 2500;

}