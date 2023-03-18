package xyz.breadloaf.voicertc.webrtc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
public class UserRespectingThreadpool {
    ExecutorService backingPool;
    HashMap<String, LinkedBlockingQueue<Runnable>> pendingTasks;
    HashMap<String, Future<?>> processingTasks;
    Thread thread;
    public UserRespectingThreadpool(int nThreads) {
        backingPool = Executors.newFixedThreadPool(nThreads);
        pendingTasks = new HashMap<>();
        processingTasks = new HashMap<>();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    processingTasks.values().removeIf(Future::isDone);
                    for (Map.Entry<String,LinkedBlockingQueue<Runnable>> tasks : pendingTasks.entrySet()) {
                        if (!processingTasks.containsKey(tasks.getKey())) {
                            LinkedBlockingQueue<Runnable> taskQueue = pendingTasks.get(tasks.getKey());
                            Runnable runnable = taskQueue.poll();
                            if (runnable != null) {
                                Future<?> submit = backingPool.submit(runnable);
                                processingTasks.put(tasks.getKey(), submit);
                            }
                        }
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void add(Runnable runnable, String string) {
        pendingTasks.computeIfAbsent(string,k -> new LinkedBlockingQueue<Runnable>()).add(runnable);
    }



}