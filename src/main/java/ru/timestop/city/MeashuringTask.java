package ru.timestop.city;

import com.price.processor.PriceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

class MeashuringTask implements PriceProcessor, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MeashuringTask.class);

    private final AtomicBoolean isExecuted = new AtomicBoolean(false);

    private final PriceProcessor origin;
    private final PriceThrottlerContext context;

    private volatile String ccyPair;
    private volatile double rate;

    MeashuringTask(PriceProcessor processor,
                   PriceThrottlerContext context) {
        this.origin = processor;
        this.context = context;
    }

    @Override
    public void run() {
        try {
            long delta = System.nanoTime();
            origin.onPrice(ccyPair, rate);
            delta = System.nanoTime() - delta;
            // TODO there are may be different condition for mark task as slow
            if (delta >= this.context.getLongOpTiming()) {
                LOGGER.info("Mark process as slow");
                this.context.getPriceProcessors().add(new SlowPriceProcessorHelper(this.origin, this.context));
            } else {
                LOGGER.info("Mark process as fast");
                this.context.getPriceProcessors().add(this.origin);
            }
        } catch (Exception e) {
            LOGGER.error("Task fail:" + origin, e);
        }
    }

    @Override
    public void onPrice(String ccyPair, double rate) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("onPrice(" + ccyPair + ", " + rate + ")");
        }
        if (!isExecuted.getAndSet(true)) {
            this.context.getPriceProcessors().remove(this);
            this.ccyPair = ccyPair;
            this.rate = rate;
            this.context.getExecutorService().submit(this);
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
    //for use search in list or map
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        } else if (other instanceof MeashuringTask) {
            return ((MeashuringTask) other).origin.equals(this.origin);
        } else if (other instanceof PriceProcessor) {
            return origin.equals(other);
        } else {
            return false;
        }
    }
}
