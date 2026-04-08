package com.grupoamarillo.sdypp.HIT3.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskRequest;
import com.grupoamarillo.sdypp.HIT1.dtos.RemoteTaskResponse;
import com.grupoamarillo.sdypp.HIT1.services.RemoteTaskService;
import com.grupoamarillo.sdypp.HIT3.config.Hit3ClusterProperties;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ClusterNodeStatus;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ClusterStatusResponse;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3CoordinatorRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ElectionRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3ElectionResponse;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3HeartbeatRequest;
import com.grupoamarillo.sdypp.HIT3.dtos.Hit3HeartbeatResponse;

import tools.jackson.databind.ObjectMapper;

@Service
public class Hit3ClusterCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(Hit3ClusterCoordinatorService.class);
    private static final String INTERNAL_PREFIX = "/internal/hit3/cluster";

    private final Hit3ClusterProperties properties;
    private final RemoteTaskService remoteTaskService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Mapa estatico de condifuracion, mapea NodeId -> host/puerto
    private final Map<Integer, NodeAddress> nodesById = new HashMap<>();
    // Mapa dinamico de nodos (vivo, ult visto, ocupado)
    private final ConcurrentHashMap<Integer, NodeRuntimeState> nodeStates = new ConcurrentHashMap<>();
    // Lider actual conocido por este nodo (-1 si no hay lider o es desconocido)
    private final AtomicInteger leaderId = new AtomicInteger(-1);
    // Flag para evitar varias elecciones concurrentes repetidas
    private final AtomicBoolean electionInProgress = new AtomicBoolean(false);
    // Ultimo heartbeat valido del lider
    private final AtomicLong lastLeaderHeartbeat = new AtomicLong(0L);
    // Cursor round robin para asignar tareas entre los nodos vivos
    private final AtomicInteger roundRobinCursor = new AtomicInteger(0);

    public Hit3ClusterCoordinatorService(
            Hit3ClusterProperties properties,
            RemoteTaskService remoteTaskService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.remoteTaskService = remoteTaskService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        initializeNodes();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isLeader() {
        return leaderId.get() == properties.getNodeId();
    }

    // Entrada principal para /api/hit3/getRemoteTask
    public RemoteTaskResponse routeIncomingTask(RemoteTaskRequest request){
        if(!isEnabled()){
            return remoteTaskService.ejecutarTareaRemota(request);
        }
        //Si soy lider realizo la asignación
        if(isLeader()){
            return assignTaskAsLeader(request);
        }

        //Si no soy lider se la envio a mi lider
        int currentLeader = leaderId.get();
        if(currentLeader > 0 && currentLeader != properties.getNodeId()){
            try{
                log.info("[Nodo {}] Recibi petición  no soy lider envio a nodo {}",properties.getNodeId(), currentLeader);
                return sendTaskToNode(currentLeader, INTERNAL_PREFIX + "/assign", request,true);
            } catch (Exception e) {
                log.warn("No se pudo contactar al líder {}. Inicio elección.", currentLeader);
                startElection();
                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo contactar al lider para asignar la tarea"
                );
            }
        }

        if (isLeader()) {
            return assignTaskAsLeader(request);
        }
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No hay líder conocido para asignar la tarea");
    }

    // ASignación a lider, el lider solo ejecuta
    public RemoteTaskResponse assignTaskAsLeader(RemoteTaskRequest request) {
        if (!isLeader()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo el lider puede asignar tareas");

        }
        List<Integer> candidates = aliveNodesSorted();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No hay nodos vivos para asignar tareas");
        }

        // Round robin sobre nodos vivos para mandarle la tarea
        int base = Math.floorMod(roundRobinCursor.getAndIncrement(), candidates.size());

        for (int i = 0; i < candidates.size(); i++) {
            int nodeId = candidates.get((base + i) % candidates.size());
            setBusy(nodeId, true); // Marco al nodo como ocupado ya que estara ejecutando la tarea

            try {
                
                if (nodeId == properties.getNodeId()) {
                    log.info("[NODO {} LIDER] Ejecutando tarea {}", properties.getNodeId(), request);
                    return remoteTaskService.ejecutarTareaRemota(request);

                }
                log.info("[Nodo {} LIDER] Envio tarea a nodo {}", properties.getNodeId(), nodeId);
                return sendTaskToNode(nodeId, INTERNAL_PREFIX + "/execute", request,true);
            } catch (Exception e) {
                markDead(nodeId);
                log.warn("Nodo {} falló ejecutando. Se interara con otro. ", nodeId);
            } finally {
                setBusy(nodeId, false); // Marco al nodo como desocupado ya que ya ejecuto la tarea
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ningun nodo pudo ejecutar la tarea");
    }

    // Endpoint interno /execute para ejecutar tareas localmente sin asignar
    public RemoteTaskResponse executeLocal(RemoteTaskRequest request) {
        log.info("[Nodo {}] Ejecutando tarea. {}", properties.getNodeId(), request);
        return remoteTaskService.ejecutarTareaRemota(request);
    }

    // Mensaje de heartbeat recibido
    public Hit3HeartbeatResponse handleHeartbeat(Hit3HeartbeatRequest request) {
        markAlive(request.getSenderId());

        // Si emisor cree conocer lider y yo no, me sincronizo
        if (request.getKnownLeaderId() > 0 && leaderId.get() <= 0) {
            leaderId.set(request.getKnownLeaderId());
        }

        return new Hit3HeartbeatResponse(properties.getNodeId(), leaderId.get(), isLeader());
    }

    // Se recibe un mensaje de election (Se aplica Bully)
    public Hit3ElectionResponse handleElection(Hit3ElectionRequest request) {
        markAlive(request.getCandidateId());

        // Regla del algoritmo Bully: si mi ID es mayor que el del candidato que me está
        // preguntando,
        // significo que yo debería ser el líder y no él. Por eso respondo "OK" (true) y
        // a la vez
        // lanzo mi propia elección en paralelo para intentar convertirme en líder.
        if (request.getCandidateId() < properties.getNodeId()) {
            new Thread(this::startElection).start();
            return new Hit3ElectionResponse(true, properties.getNodeId());
        }

        return new Hit3ElectionResponse(false, properties.getNodeId());
    }

    // Mensaje coordinador recibido. Indica quien es el nuevo lider.
    public void handleCoordinator(Hit3CoordinatorRequest request) {
        if (request.getLeaderId() <= 0)
            return; // No hay lider

        leaderId.set(request.getLeaderId());
        electionInProgress.set(false);
        lastLeaderHeartbeat.set(System.currentTimeMillis());
        markAlive(request.getLeaderId());

        log.info("Nodo {} reconoce lider {}", properties.getNodeId(), request.getLeaderId());
    }

    // Tick periodico para heartbeats
    @Scheduled(fixedDelayString = "${hit3.cluster.heartbeat-interval-ms:1000}")
    public void heartbeatTick() {
        if (!isEnabled())
            return; // Si el nodo no esta habilitado, no hago nada

        markAlive(properties.getNodeId());

        if (isLeader()) {
            // Lider pinguea a sus followers para saber cual esta vivo/muerto
            for (NodeAddress node : nodesById.values()) {
                if (node.id() == properties.getNodeId())
                    continue; // Si soy yo continuo

                boolean ok = pingNode(node.id(),
                        new Hit3HeartbeatRequest(properties.getNodeId(), properties.getNodeId()));

                if (ok)
                    markAlive(node.id());
                else
                    markDead(node.id());

            }
            return;
        }
        // Follower verifica al lider
        int currentLeader = leaderId.get();
        if (currentLeader <= 0) {
            // No hay un leader
            startElection();
            return;
        }

        boolean ok = pingNode(currentLeader, new Hit3HeartbeatRequest(properties.getNodeId(), currentLeader));

        if (ok) {
            // Seteo la ultima respuesta del lider
            lastLeaderHeartbeat.set(System.currentTimeMillis());
            return;
        }

        // El lider murio, inicio una nueva eleccion
        long elapsed = System.currentTimeMillis() - lastLeaderHeartbeat.get();
        if (elapsed >= properties.getHeartbeatTimeoutMs()) {
            log.warn("Timeout de lider detectado en nodo {}", properties.getNodeId());
            startElection();
        }
    }

    // Proceso de eleccion Bully
    public synchronized void startElection() {
        if (!isEnabled())
            return;
        if (electionInProgress.get())
            return; // Ya hay una eleccion en curso

        electionInProgress.set(true); // Marco la eleccion como en curso para evitar multiples

        List<NodeAddress> higherNodes = nodesById.values().stream()
                .filter(n -> n.id() > properties.getNodeId()) // Filtro por nodos con ID mayor que el mio
                .sorted(Comparator.comparingInt(NodeAddress::id)) // Ordeno por ID
                .collect(Collectors.toList()); // Colecto en una lista

        boolean higherAlive = false;

        // Pregunto a nodos con mayor ID
        for (NodeAddress node : higherNodes) {
            Hit3ElectionResponse response = sendElection(node.id());
            if (response != null && response.isOk()) {
                higherAlive = true; // Hay un nodo con ID mayor que el mio que esta vivo
            }
        }

        // Si nadie mayor que yo respondio, entonces yo soy el lider
        if (!higherAlive) {
            becomeLeader();
            return;
        }

        // Si alguien mayor respondio, espero que lo anuncie el coordinator
        long deadline = System.currentTimeMillis() + properties.getElectionTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (!electionInProgress.get())
                return; // Ya no hay una eleccion en curso entonces me voy

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Si nadie anuncio un lider en el timeout, me convierto en lider
        if (electionInProgress.get()) {
            becomeLeader();
        }

    }

    private void becomeLeader() {
        leaderId.set(properties.getNodeId()); // Me convierto en lider
        electionInProgress.set(false);
        lastLeaderHeartbeat.set(System.currentTimeMillis());
        markAlive(properties.getNodeId());

        // Digo que soy el nuevo lider
        Hit3CoordinatorRequest msg = new Hit3CoordinatorRequest(properties.getNodeId());

        // Anuncio a todos los demas que me converti en lider
        for (NodeAddress node : nodesById.values()) {
            if (node.id() == properties.getNodeId())
                continue; // Si soy yo continuo
            sendVoid(node.id(), INTERNAL_PREFIX + "/coordinator", msg); // Anuncio al nodo que soy el nuevo lider
        }
        log.info("Nodo {} ahora es líder", properties.getNodeId());
    }

    public Hit3ClusterStatusResponse getClusterStatus() {
        // Se construye la lista de estados de cada nodo:
        // 1. Se toman todos los nodos configurados (nodesById) y se ordenan por ID.
        // 2. Por cada nodo se obtiene su estado dinámico (nodeStates).
        // 3. Se crea un Hit3ClusterNodeStatus con:
        //    - id y host fijos del nodo configurado.
        //    - alive: true solo si existe registro en nodeStates y está marcado como vivo.
        //    - leader: true si el ID coincide con el líder actual.
        //    - lastSeenMs: 0 si nunca se vio; si no, el valor guardado.
        //    - busy: true solo si existe registro y está marcado como ocupado.
        List<Hit3ClusterNodeStatus> nodes = nodesById.values().stream()
                .sorted(Comparator.comparingInt(NodeAddress::id))
                .map(a -> {
                    NodeRuntimeState s = nodeStates.get(a.id());
                    return new Hit3ClusterNodeStatus(
                            a.id(),
                            a.host(),
                            s != null && s.alive(),
                            leaderId.get() == a.id(),
                            s == null ? 0L : s.lastSeenMs(),
                            s != null && s.busy());
                })
                .collect(Collectors.toList());
        return new Hit3ClusterStatusResponse(
            properties.getNodeId(),
            leaderId.get(),
            isLeader(),
            electionInProgress.get(),
            nodes
        );
    }

    private Hit3ElectionResponse sendElection(int nodeId){
        try{
            // Envío al nodo remoto mi intención de ser líder (Hit3ElectionRequest con mi ID)
            String body = sendJson(nodeId, INTERNAL_PREFIX + "/election",
                    new Hit3ElectionRequest(properties.getNodeId()),
                 Duration.ofMillis(properties.getControlTimeoutMs()));
            // Como obtuve respuesta, marco ese nodo como vivo
            markAlive(nodeId);
            // Convierto la respuesta JSON en objeto Hit3ElectionResponse y la devuelvo
            return objectMapper.readValue(body, Hit3ElectionResponse.class);
        } catch (Exception e) {
            markDead(nodeId); 
            return null;
        }
    }

    private boolean pingNode(int nodeId, Hit3HeartbeatRequest request){
        try{
            String body = sendJson(nodeId, INTERNAL_PREFIX + "/heartbeat", request,
                              Duration.ofMillis(properties.getControlTimeoutMs())
            );

            Hit3HeartbeatResponse response = objectMapper.readValue(body, Hit3HeartbeatResponse.class);
            markAlive(nodeId);

            //Si el otro nodo conoce al lider valido y me sincronizo
            if (response.getLeaderId() > 0){
                leaderId.set(response.getLeaderId());
            }
            return true; // Respondio, esta vivo
        }catch (Exception e){
            //No respondio, esta muerto
            markDead(nodeId); 
            return false; 
        }
    }

    private RemoteTaskResponse sendTaskToNode(int nodeId, String path, RemoteTaskRequest request, boolean isTask){
        try{
            Duration timeout = isTask 
                ? Duration.ofMillis(properties.getTaskTimeoutMs())
                : Duration.ofMillis(properties.getControlTimeoutMs());
            String body = sendJson(nodeId, path, request,timeout);
            return objectMapper.readValue(body, RemoteTaskResponse.class); //Mapeo el json a una response
        } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo ejecutar en nodo " + nodeId);
        }
    }

    private void sendVoid(int nodeId, String path, Object payload){
        try{
            sendJson(nodeId, path, payload,
                 Duration.ofMillis(properties.getControlTimeoutMs())
            );
        } catch (Exception e){
            markDead(nodeId);
        }
    }

    /**
     * Envía un objeto Java (payload) como JSON vía POST HTTP a un nodo específico del cluster.
     * 
     * Sirve para:
     * - Transmitir mensajes internos del protocolo (heartbeats, elecciones, coordinador).
     * - Delegar tareas remotas al endpoint /execute del nodo destino.
     * - Recibir la respuesta en formato String para luego ser des-serializada al DTO correspondiente.
     * 
     * @param nodeId  identificador del nodo destino (se busca su IP/puerto en nodesById)
     * @param path    ruta relativa del endpoint interno (ej: /internal/hit3/cluster/heartbeat)
     * @param payload objeto Java a serializar como JSON en el cuerpo del POST
     * @return        cuerpo de la respuesta HTTP como String
     * @throws IllegalArgumentException si el nodo no existe en la configuración
     * @throws IllegalStateException    si el código de respuesta es >= 400
     * @throws Exception                si ocurre un error de I/O o timeout (2 s)
     */
    private String sendJson(int nodeId, String path, Object payload, Duration timeout) throws Exception{
        NodeAddress address = nodesById.get(nodeId);

        if (address == null){
            throw new IllegalArgumentException("Nodo desconocido: " + nodeId);
        }
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://" + address.host() + ":" + address.port() + path))
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if(response.statusCode() >= 400){
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        return response.body();
    }

    private List<Integer> aliveNodesSorted(){
        List<Integer> ids = new ArrayList<>();

        for (Map.Entry<Integer, NodeRuntimeState> entry : nodeStates.entrySet()){
            if (entry.getValue().alive()){
                ids.add(entry.getKey());
            }
        }

        if(!ids.contains(properties.getNodeId())){
            ids.add(properties.getNodeId());
        }
        // Ordena la lista de IDs de nodos vivos de menor a mayor para que el líder recorra
        // siempre en el mismo orden y el round-robin sea determinista.
        ids.sort(Integer::compareTo);
        return ids;
    }
    /**
     * Inicializa la lista de nodos del cluster a partir de la propiedad 'nodes' que contiene
     * cadenas con formato "id:host:port" separadas por comas.
     * 
     * Para cada nodo:
     * - Parsea id, host y puerto.
     * - Registra la dirección fija en nodesById.
     * - Crea un NodeRuntimeState marcando:
     *   * alive = true si el id coincide con el nodo actual, false en caso contrario.
     *   * lastSeenMs = tiempo actual.
     *   * busy = false.
     * 
     * Si tras el bucle el nodo actual no figura en la configuración, se auto-registra
     * usando las propiedades selfHost y selfPort, y se marca como vivo.
     */
    private void initializeNodes(){
        for (String rawNode : properties.getNodes().split(",")){
            String[] parts = rawNode.trim().split(":");

            if (parts.length != 3) continue; // Si no tiene 3 partes (id, host, port), salta

            int id = Integer.parseInt(parts[0].trim());
            String host = parts[1].trim();
            int port = Integer.parseInt(parts[2].trim());
            
            nodesById.put(id, new NodeAddress(id, host, port));
            nodeStates.put(id, new NodeRuntimeState(id == properties.getNodeId(), System.currentTimeMillis(), false));

        }

        if (!nodesById.containsKey(properties.getNodeId())){
            nodesById.put(
                properties.getNodeId(),
                new NodeAddress(properties.getNodeId(), properties.getSelfHost(), properties.getSelfPort())
            );
            nodeStates.put(properties.getNodeId(), new NodeRuntimeState(true, System.currentTimeMillis(), false));
        }
    }
    /**
     * Marca un nodo como VIVO en el mapa nodeStates.
     * Usa ConcurrentHashMap.compute() para actualizar de forma atómica:
     * 1. Si no existe entrada para ese nodeId, crea un nuevo NodeRuntimeState
     *    con alive=true, timestamp actual y busy=false.
     * 2. Si ya existe, reutiliza el flag busy actual y solo actualiza
     *    alive=true y lastSeenMs=ahora.
     * compute() garantiza que la operación es thread-safe sin necesidad
     * de sincronización externa.
     */
    private void markAlive(int nodeId) {
        nodeStates.compute(nodeId, (id, state) -> {
            if (state == null) {
                return new NodeRuntimeState(true, System.currentTimeMillis(), false);
            }
            return new NodeRuntimeState(true, System.currentTimeMillis(), state.busy());
        });
    }

    /**
     * Marca un nodo como MUERTO (alive=false) en nodeStates.
     * No permite marcar como muerto al propio nodo.
     * Emplea computeIfPresent() para actualizar solo si la clave existe;
     * mantiene el lastSeenMs previo y fuerza busy=false.
     * Al igual que compute(), es atómico y thread-safe.
     */
    private void markDead(int nodeId) {
        if (nodeId == properties.getNodeId()) return;
        nodeStates.computeIfPresent(nodeId, (id, state) ->
                new NodeRuntimeState(false, state.lastSeenMs(), false));
    }

    private void setBusy(int nodeId, boolean busy) {
        nodeStates.compute(nodeId, (id, state) -> {
            if (state == null) {
                return new NodeRuntimeState(true, System.currentTimeMillis(), busy);
            }
            return new NodeRuntimeState(state.alive(), state.lastSeenMs(), busy);
        });
    }

    private record NodeAddress(int id, String host, int port) {}
    private record NodeRuntimeState(boolean alive, long lastSeenMs, boolean busy) {}
}
