package com.grupoamarillo.sdypp.HIT3.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ClusterStatusResponse;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3CoordinatorRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ElectionRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ElectionResponse;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3HeartbeatRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3HeartbeatResponse;
import com.grupoamarillo.sdypp.HIT3.services.Hit3ClusterCoordinatorService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;



@RestController
@RequestMapping("/internal/hit3/cluster")
public class Hit3InternalController {
    private final Hit3ClusterCoordinatorService clusterService;
    
    public Hit3InternalController(Hit3ClusterCoordinatorService clusterService){
        this.clusterService = clusterService;
    }

    //Heartbeat: pregunta si sigue vivo y quien es el lider actual
    @PostMapping("/heartbeat")
    public ResponseEntity<Hit3HeartbeatResponse> heartbeat(@RequestBody Hit3HeartbeatRequest request) {
        return ResponseEntity.ok(clusterService.handleHeartbeat(request));
    }

    //Mensaje Bully: un candidato inicia eleccion y pregunta a nodos superiores
    @PostMapping("/election")
    public ResponseEntity<Hit3ElectionResponse> election(@RequestBody Hit3ElectionRequest request){
        return ResponseEntity.ok(clusterService.handleElection(request));
    }

    //Mensaje de anuncio final de líder
    @PostMapping("/coordinator")
    public ResponseEntity<Void> coordinator(@RequestBody Hit3CoordinatorRequest request) {
        clusterService.handleCoordinator(request);
        return ResponseEntity.ok().build();

    }
    
    // Solo debería recibirlo el líder: "asigname esta tarea"
    @PostMapping("/assign")
    public ResponseEntity<RemoteTaskResponse> assign(@Valid @RequestBody RemoteTaskRequest request) {
        return ResponseEntity.ok(clusterService.assignTaskAsLeader(request));
    }
    
    //Ejecutar tarea local sin reasignación
    @PostMapping("/execute")
    public ResponseEntity<RemoteTaskResponse> execute(@Valid @RequestBody RemoteTaskRequest request) {
        return ResponseEntity.ok(clusterService.executeLocal(request));
    }

    //Estado del cluster
    @GetMapping("/status")
    public ResponseEntity<Hit3ClusterStatusResponse> status() {
        return ResponseEntity.ok(clusterService.getClusterStatus());
    }
    
    
    
}
