package com.soubhagya.systivex.localmodel;

public interface MonotonicClock {

    long nanoTime();

    static MonotonicClock system() {
        return System::nanoTime;
    }
}
