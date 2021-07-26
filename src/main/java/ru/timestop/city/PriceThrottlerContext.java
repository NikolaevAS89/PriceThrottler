package ru.timestop.city;

import com.price.processor.PriceProcessor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

class PriceThrottlerContext {
    public static long LONG_OP_TIMING = 500L;

    private final Map<String, Double> states;

    private final ExecutorService executors;
    private final List<PriceProcessor> priceProcessors;

    PriceThrottlerContext() {
        priceProcessors = new CopyOnWriteArrayList<>();
        states = new ConcurrentHashMap<>(201, 1.0f);
        executors = Executors.newCachedThreadPool(new PriceThrottlerThreadFactory());
    }

    Double getState(String ccyPair) {
        return states.get(ccyPair);
    }

    void setState(String ccyPair, double rate) {
        this.states.put(ccyPair, rate);
    }

    List<PriceProcessor> getPriceProcessors() {
        return this.priceProcessors;
    }

    public ExecutorService getExecutorService() {
        return this.executors;
    }

    public long getLongOpTiming() {
        return LONG_OP_TIMING;
    }
}

class PriceThrottlerThreadFactory implements ThreadFactory {
    private final static String THREAD_GROUP_NAME = "PriceThrottlerThreadGroup";
    private final static String THREAD_COMMON_NAME = "PriceThrottlerThread";

    private final static ThreadGroup threadGroup = new ThreadGroup(THREAD_GROUP_NAME);
    private static int cnt = 0;

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(threadGroup, THREAD_COMMON_NAME + cnt++);
    }
}