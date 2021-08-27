package com.tangle.tanglelibrary;

import android.os.SystemClock;

public class Stopwatch {

    boolean pause;
    boolean paused;
    long lastPauseTime;
    long startTime;
    int time;

    public Stopwatch() {
        this.pause = true;
        this.paused = false;
        this.lastPauseTime = 0;
        this.startTime = 0;
        this.time = 0;
    }

    public int start() {
        if (pause) {

            pause = false;
            startTime = SystemClock.elapsedRealtime();
            if (!paused) {
                time = 0;
            }
            return time;
        }
        return time + (int) (SystemClock.elapsedRealtime() - startTime);
    }

    public int stop() {
        pause = true;

        paused = false;
        time = 0;
        return time;
    }

    public int pause() {
        if (!pause) {
            pause = true;
            paused = true;
            lastPauseTime = SystemClock.elapsedRealtime();
            time += lastPauseTime - startTime;
        }
        return time;
    }

    public int getTime(){
        if (pause){
            return time;
        } else if (!paused){
            return (int) (SystemClock.elapsedRealtime() -startTime);
        } else {
            return time + (int) (SystemClock.elapsedRealtime() - startTime);
        }
    }
}
