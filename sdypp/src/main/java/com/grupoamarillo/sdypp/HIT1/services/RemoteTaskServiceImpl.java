package com.grupoamarillo.sdypp.HIT1.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grupoamarillo.sdypp.HIT1.docker.CommandResult;
import com.grupoamarillo.sdypp.HIT1.docker.DockerCommandRunner;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.dtos.TaskServiceRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class RemoteTaskServiceImpl implements RemoteTaskService {
    private static final Pattern IMAGE_PATTERN = Pattern
            .compile("^[a-z0-9]+([._-][a-z0-9]+)*(/[a-z0-9]+([._-][a-z0-9]+)*)*(:[\\w][\\w.-]{0,127})?$");

    private static final Pattern PORT_PATTERN = Pattern.compile(".*:(\\d+)$");
    private final DockerCommandRunner dockerRunner;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String taskServiceHost;

    public RemoteTaskServiceImpl(DockerCommandRunner dockerRunner, ObjectMapper objectMapper) {
        this.dockerRunner = dockerRunner;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String configuredHost = System.getenv("TASK_SERVICE_HOST");
        this.taskServiceHost = (configuredHost == null || configuredHost.isBlank()) ? "localhost" : configuredHost;
    }

    // Redefino el metodo de la interface
    @Override
    public RemoteTaskResponse ejecutarTareaRemota(RemoteTaskRequest request) {
        long inicio = System.currentTimeMillis();
        String containerId = null;

        try {
            validarImagenDocker(request.getImagenDocker());

            ejecutarDockerPull(request.getImagenDocker());
            containerId = ejecutarDockerRun(request.getImagenDocker());
            String hostPort = obtenerPuertoHost(containerId);

            TaskServiceRequest payload = new TaskServiceRequest(
                    request.getCalculo(),
                    request.getParametros(),
                    request.getDatosAdicionales());

            JsonNode taskResponse = invocarServicioTareaConReintentos(hostPort, payload);

            long duracion = System.currentTimeMillis() - inicio;
            return new RemoteTaskResponse(
                    taskResponse.path("status").asText("OK"),
                    taskResponse.path("resultado"),
                    taskResponse.path("mensaje").asText("Tarea ejecutada"),
                    containerId,
                    duracion);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            long duracion = System.currentTimeMillis() - inicio;
            return new RemoteTaskResponse(
                    "ERROR",
                    null,
                    e.getMessage(),
                    containerId,
                    duracion);
        } finally {
            if (containerId != null && !containerId.isBlank()) {
                //dockerRunner.run(List.of("docker", "stop", containerId));
                //dockerRunner.run(List.of("docker", "rm", containerId));
            }
        }
    }

    private void validarImagenDocker(String imagenDocker) {
        if (!IMAGE_PATTERN.matcher(imagenDocker).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El formato de imagenDocker no es válido");
        }
    }

    private void ejecutarDockerPull(String imagenDocker) {
        CommandResult result = dockerRunner.run(List.of("docker", "pull", imagenDocker));
        if (result.exitCode() != 0) { // En el caso que la descarga falla
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se pudo descargar la imagen Docker: "
                            + result.stderr());
        }
    }

    private String ejecutarDockerRun(String imagenDocker) {
        CommandResult result = dockerRunner.run(List.of("docker", "run", "-d", "-P", imagenDocker));
        if (result.exitCode() != 0) { // En el caso que la ejecucion falla
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo iniciar el contenedor: "
                            + result.stderr());
        }
        String containerId = result.stdout().trim();
        if (containerId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Docker run no devolvió containerId");
        }
        return containerId;
    }

    private String obtenerPuertoHost(String containerId) {
        CommandResult result = dockerRunner.run(List.of("docker", "port", containerId, "8080"));
        if (result.exitCode() != 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo obtener el puerto del contenedor: " + result.stderr());
        }

        String output = result.stdout().trim();
        java.util.regex.Matcher matcher = PORT_PATTERN.matcher(output);

        if (!matcher.find()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo parsear el puerto mapeado: " + output);
        }
        return matcher.group(1);
    }

    // Metodo que se encarga de invocar al servicio tarea una vez se encuentra
    // levantado:
    private JsonNode invocarServicioTarea(String hostPort, TaskServiceRequest payload) {
        try {
            // Paso a String el payload
            String body = objectMapper.writeValueAsString(payload);
            // Armo la request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + taskServiceHost + ":" + hostPort + "/ejecutar"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            // Envio la petición HTTP y guardo la respuesta:
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "El servicio tarea respondió con error HTTP " + response.statusCode() + ": " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo invocar el servicio tarea: " + e.getMessage());
        }

    }

    private JsonNode invocarServicioTareaConReintentos(String hostPort, TaskServiceRequest payload) {
        ResponseStatusException ultimoError = null;
        for (int intento = 1; intento <= 10; intento++) {
            try {
                return invocarServicioTarea(hostPort, payload);
            } catch (ResponseStatusException e) {
                ultimoError = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(
                            HttpStatus.BAD_GATEWAY,
                            "Interrumpido esperando disponibilidad del servicio tarea");
                }
            }
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "No se pudo invocar el servicio tarea luego de múltiples reintentos: "
                        + (ultimoError == null ? "" : ultimoError.getReason()));
    }

}
