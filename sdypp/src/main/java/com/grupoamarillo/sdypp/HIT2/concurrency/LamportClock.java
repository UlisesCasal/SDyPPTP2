package com.grupoamarillo.sdypp.HIT2.concurrency;

import java.util.concurrent.atomic.AtomicLong;


public class LamportClock {
    //Usa atomiclong para evitar condiciones de carrera en la modificación
    //de este atributo. Ya que varios lo van a estar modificando
    private final AtomicLong clock = new AtomicLong(0);

    //Incrementa el reloj en 1 
    //Se llama antes de procesar cualquier evento
    public long tick(){
        return clock.incrementAndGet();
    }

    //Cada vez que recibe un mensaje, actualiza su reloj al máximo entre el reloj actual y el recibido
    public long receive(long receivedTimestamp){
        return clock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);
    }
    
    //Devuelvo el clock sin modificarlo
    public long current(){
        return clock.get();
    }

}
