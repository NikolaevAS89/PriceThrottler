package ru.timestop.city;

import com.price.processor.PriceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

public class PriceThrottler implements PriceProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PriceThrottler.class);

    private final PriceThrottlerContext context;

    public PriceThrottler() {
        this.context = new PriceThrottlerContext();
    }

    public void onPrice(String ccyPair, double rate) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("onPrice(" + ccyPair + ", " + rate + ")");
        }
        this.context.setState(ccyPair, rate);
        for (PriceProcessor processor : this.context.getPriceProcessors()) {
            try {
                this.context.getExecutorService().submit(() -> {
                    try {
                        processor.onPrice(ccyPair, rate);
                    } catch (Exception e) {
                        LOGGER.error("Task fail:" + processor, e);
                    }
                });
            } catch (RejectedExecutionException e) {
                LOGGER.warn("Process task rejected", e);
            } catch (NullPointerException e) {
                LOGGER.error("Process task is NULL", e);
            }
        }
    }

    public void subscribe(PriceProcessor priceProcessor) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("subscribe(" + priceProcessor + ")");
        }
        this.context.getPriceProcessors().add(new MeashuringTask(priceProcessor, context));
    }

    public void unsubscribe(PriceProcessor priceProcessor) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("unsubscribe(" + priceProcessor + ")");
        }
        this.context.getPriceProcessors().remove(priceProcessor);
    }
}
