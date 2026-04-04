package com.grupoamarillo.sdypp.HIT2.concurrency;

import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskRequest;
import com.grupoamarillo.sdypp.HIT2.dtos.Hit2TaskResponse;

public interface TaskExecutor {
    Hit2TaskResponse execute(Hit2TaskRequest request, long lamportTs, long posicionEnCola);
}
