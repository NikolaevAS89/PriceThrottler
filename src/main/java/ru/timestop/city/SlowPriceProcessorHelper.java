package ru.timestop.city;

import com.price.processor.PriceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

class SlowPriceProcessorHelper implements PriceProcessor, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlowPriceProcessorHelper.class);
    private final PriceThrottlerContext context;

    private final AtomicBoolean isExecuted = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Long> timing = new ConcurrentHashMap<>(201, 1.0f);
    private final PriceProcessor origin;

    public SlowPriceProcessorHelper(PriceProcessor origin,
                                    PriceThrottlerContext context) {
        this.origin = origin;
        this.context = context;
    }

    @Override
    //for use search in list or map
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        } else if (other instanceof SlowPriceProcessorHelper) {
            return ((SlowPriceProcessorHelper) other).origin.equals(this.origin);
        } else if (other instanceof PriceProcessor) {
            return origin.equals(other);
        } else {
            return false;
        }
    }

    @Override
    public void onPrice(String ccyPair, double rate) {
        timing.putIfAbsent(ccyPair, System.nanoTime());
        if (!isExecuted.get()) {
            try {
                // i decide that this function will be called to often for triggering processor
                this.context.getExecutorService().submit(this);
            } catch (RejectedExecutionException e) {
                LOGGER.warn("Process task rejected, will try late", e);
            } catch (NullPointerException e) {
                LOGGER.error("Process task is NULL", e);
            }
        }
    }

    @Override
    public void subscribe(PriceProcessor priceProcessor) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("subscribe(" + priceProcessor + ")");
        }
        if (!this.origin.equals(priceProcessor))
            this.origin.subscribe(priceProcessor);
    }

    @Override
    public void unsubscribe(PriceProcessor priceProcessor) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("unsubscribe(" + priceProcessor + ")");
        }
        if (!this.origin.equals(priceProcessor))
            this.origin.unsubscribe(priceProcessor);
    }

    @Override
    public void run() {
        try {
            if (!isExecuted.getAndSet(true)) {
                Map.Entry<String, Long> oldest = null;
                for (Map.Entry<String, Long> entry : timing.entrySet()) {
                    if (oldest == null) {
                        oldest = entry;
                    } else {
                        oldest = (oldest.getValue() > entry.getValue()) ? (entry) : (oldest);
                    }
                }
                if (oldest != null) {
                    String key = oldest.getKey();
                    timing.remove(key);
                    origin.onPrice(key, this.context.getState(key));
                }
                isExecuted.set(false);
            }
        } catch (Exception e) {
            LOGGER.error("Task fail:" + origin, e);
        }
    }
}