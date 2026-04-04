package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.grupoamarillo.sdypp.HIT1.docker.CommandResult;
import com.grupoamarillo.sdypp.HIT1.docker.DockerCommandRunner;
import com.grupoamarillo.sdypp.HIT1.dtos.TaskServiceRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

@Component
public class TaskExecutorImpl implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorImpl.class);
    
    private static final Pattern IMAGE_PATTERN = Pattern
            .compile("^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*(:[\\w][\\w.-]{0,127})?$");
    
    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)$");
    
    private final DockerCommandRunner dockerRunner;
    
    private final ObjectMapper objectMapper;
    
    private final HttpClient httpClient;
    
    private final LamportClock lamportClock;
    
    private final String taskServiceHost;

    public TaskExecutorImpl(DockerCommandRunner dockerRunner,
            ObjectMapper objectMapper,
            LamportClock lamportClock) {
        this.dockerRunner = dockerRunner;
        this.objectMapper = objectMapper;
        this.lamportClock = lamportClock;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String host = System.getenv("TASK_SERVICE_HOST");
        this.taskServiceHost = (host == null || host.isBlank()) ? "localhost" : host;
    }

    @Override
    public Hit2TaskResponse execute(Hit2TaskRequest request, long lamportTs, long posicionEnCola) {
        long inicio = System.currentTimeMillis();
        String containerId = null;
        try {
            // Validar imagen
            if (!IMAGE_PATTERN.matcher(request.getImagenDocker()).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Formato de imagenDocker inválido");
            }
            // Pull
            CommandResult pullResult = dockerRunner.run(
                    List.of("docker", "pull", request.getImagenDocker()));
            if (pullResult.exitCode() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No se pudo descargar la imagen: " + pullResult.stderr());
            }
            // Run
            CommandResult runResult = dockerRunner.run(
                    List.of("docker", "run", "-d", "-P", request.getImagenDocker()));
            if (runResult.exitCode() != 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo iniciar el contenedor: " + runResult.stderr());
            }
            containerId = runResult.stdout().trim();
            // Port
            CommandResult portResult = dockerRunner.run(
                    List.of("docker", "port", containerId, "8080"));
            Matcher matcher = PORT_PATTERN.matcher(portResult.stdout().trim());
            if (!matcher.find()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo obtener el puerto");
            }
            String hostPort = matcher.group(1);
            // Invoke con reintentos
            TaskServiceRequest payload = new TaskServiceRequest(
                    request.getCalculo(),
                    request.getParametros(),
                    request.getDatosAdicionales());
            JsonNode taskResponse = invocarConReintentos(hostPort, payload);
            long duracion = System.currentTimeMillis() - inicio;
            // Tick del reloj al responder
            long responseTs = lamportClock.tick();
            return new Hit2TaskResponse(
                    taskResponse.path("status").isMissingNode() ? "OK" : taskResponse.path("status").asText(),
                    taskResponse.path("resultado"),
                    taskResponse.path("mensaje").isMissingNode() ? "Tarea ejecutada" : taskResponse.path("mensaje").asText(),
                    containerId,
                    duracion,
                    responseTs,
                    posicionEnCola);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            long duracion = System.currentTimeMillis() - inicio;
            return new Hit2TaskResponse("ERROR", null, e.getMessage(),
                    containerId, duracion, lamportClock.tick(), posicionEnCola);
        } finally {
            if (containerId != null && !containerId.isBlank()) {
                dockerRunner.run(List.of("docker", "stop", containerId));
                dockerRunner.run(List.of("docker", "rm", containerId));
            }
        }
    }
    private JsonNode invocarConReintentos(String hostPort, TaskServiceRequest payload) {
        ResponseStatusException ultimoError = null;
        for (int i = 1; i <= 10; i++) {
            try {
                String body = objectMapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + taskServiceHost + ":" + hostPort + "/ejecutar"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Error HTTP " + response.statusCode());
                }
                return objectMapper.readTree(response.body());
            } catch (ResponseStatusException e) {
                ultimoError = e;
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interrumpido");
                }
            } catch (Exception e) {
                ultimoError = new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interrumpido");
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Reintentos agotados: " + (ultimoError == null ? "" : ultimoError.getReason()));
    }
}