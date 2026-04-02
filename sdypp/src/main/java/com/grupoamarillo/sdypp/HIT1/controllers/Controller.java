package com.grupoamarillo.sdypp.HIT1.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;

import io.micrometer.core.ipc.http.HttpSender.Response;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController //Digo q es un REST CONTROLLER PARA QUE RECIBA REQUESTS
@RequestMapping("/api/hit1") //Expongo la ruta Hit1
public class Controller {

    private final RemoteTaskService remoteTaskService;
    
    public Controller(RemoteTaskService remoteTaskService) {
        this.remoteTaskService = remoteTaskService;
    }

    @PostMapping("/getRemoteTask")
    public ResponseEntity<RemoteTaskResponse> getRemoteTask(@Valid @RequestBody RemoteTaskRequest request) {
        //El valid valida que los campos del request no sean nulos
        //Si son nulos, lanza una excepción

        RemoteTaskResponse response = remoteTaskService.ejecutarTareaRemota(request);
        return ResponseEntity.ok(response);
        
    }
    
}
