package com.grupoamarillo.sdypp.HIT3.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3HeartbeatResponse {
    // quién respondió
    private int senderId;
    // líder que el receptor conoce
    private int leaderId;
    // true si el que responde es líder
    private boolean leader;
}
