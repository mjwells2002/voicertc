package xyz.breadloaf.voicertc.webrtc.sctp;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class PooledTimer {
    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> task;
    long scheduledAt = Long.MAX_VALUE;

    public PooledTimer(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;

    }

    abstract void tick();

    public void setNextRun(long time) {
        long now = System.currentTimeMillis();
        long when = now + time;
        if (when < this.scheduledAt) {
            if (this.task != null) {
                this.task.cancel(true);
            }

            this.task = this.scheduledExecutorService.schedule(new Runnable() {
                public void run() {
                    try {
                        tick();
                        scheduledAt = Long.MAX_VALUE;
                    } catch (Throwable ignored) {

                    }
                }
            },time, TimeUnit.MILLISECONDS);

            this.scheduledAt = when;
        }
    }
}
