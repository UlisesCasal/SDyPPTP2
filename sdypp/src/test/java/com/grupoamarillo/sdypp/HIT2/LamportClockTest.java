package com.grupoamarillo.sdypp.HIT2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.grupoamarillo.sdypp.HIT2.concurrency.LamportClock;

class LamportClockTest {

    @Test
    void tickShouldIncrementClockSequentially() {
        LamportClock clock = new LamportClock();

        assertEquals(1, clock.tick());
        assertEquals(2, clock.tick());
        assertEquals(3, clock.tick());
    }

    @Test
    void receiveShouldAdvanceClockToMaxOfCurrentAndReceivedPlusOne() {
        LamportClock clock = new LamportClock();

        assertEquals(1, clock.tick());
        assertEquals(2, clock.tick());
        assertEquals(5, clock.receive(4));
        assertEquals(6, clock.receive(3));
        assertEquals(6, clock.current());
    }
}
