package com.grupoamarillo.sdypp.HIT3.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT3.services.Hit3TaskRouterService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hit3")
public class Hit3Controller {
    private final Hit3TaskRouterService router;
    
    public Hit3Controller(Hit3TaskRouterService router){
        this.router = router;
    }

    @PostMapping("/getRemoteTask")
    public ResponseEntity<RemoteTaskResponse> getRemoteTask( @Valid @RequestBody RemoteTaskRequest request){
        return ResponseEntity.ok(router.route(request));
        
    }
}
