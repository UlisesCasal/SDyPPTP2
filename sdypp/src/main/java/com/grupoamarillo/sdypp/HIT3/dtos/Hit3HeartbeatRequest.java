package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3HeartbeatRequest {
    // quién manda el heartbeat
    private int senderId;
    // líder que el emisor cree actual
    private int knownLeaderId;
}
