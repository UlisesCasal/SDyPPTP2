package com.grupoamarillo.sdypp.HIT3.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hit3ClusterStatusResponse {
    // ID lógico del nodo que responde /status
    private int selfNodeId;
    // líder actual
    private int leaderId;
    // si self es líder
    private boolean leader;
    // si este nodo está en medio de una elección
    private boolean electionInProgress;
    // snapshot de nodos del cluster
    private List<Hit3ClusterNodeStatus> nodes;}
