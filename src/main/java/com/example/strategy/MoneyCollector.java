package com.example.strategy;

import com.example.model.rest.*;
import com.example.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class MoneyCollector implements Runnable {

    private static final long minQty = 5;
    private static final long minAsk = 15;
    private static final long minBid = 79;

    private static final int averageCount = 20;

    private static final Logger logger = LoggerFactory.getLogger(MoneyCollector.class);

    private final Platform platform;

    private static final Random rg = new Random();

    public MoneyCollector(Platform platform) {
        this.platform = platform;
    }

    @Override
    public void run() {
        final var fetchedInstruments = platform.instruments();
        final var fetchedPortfolio = platform.portfolio();

        if (fetchedPortfolio instanceof PortfolioResponse.Other other) {
            logger.info("portfolio fetch failed {}", other);
        }

        if (fetchedInstruments instanceof InstrumentsResponse.Other other) {
            logger.info("instruments fetch failed {}", other);
        }

        if (fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio && fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
            logger.info("My cash {}",portfolio.cash());
            //for (final var element : portfolio.portfolio()) {
            //    final var instrument = element.instrument();
            //    final var orders = platform.history(new HistoryRequest(instrument));
            //    logger.info("instrument {} has orders {}", instrument, orders);
            //}


            final var selectedForBuy = instruments
                    .available()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.10)
                    .toList();

            for (final var instrument : selectedForBuy) {
                //logger.info("My cash {}",portfolio.cash());
                final var history = platform.history(new HistoryRequest(instrument));

                if (history instanceof HistoryResponse.History correct) {

                    final long orderSum = correct
                                    .bought()
                                    .stream()
                                    .limit(averageCount)
                                    .mapToLong(b->b.offer().price()*b.offer().qty())
                                    .sum();
                    final long orderCount = correct
                                    .bought()
                                    .stream()
                                    .limit(averageCount)
                                    .mapToLong(b->b.offer().qty())
                                    .sum();
                    if (orderCount == 0) continue;
                    final long bid = orderSum/orderCount<10?minBid:(long) (0.9*orderSum/orderCount);


                    final var qty = 1+rg.nextInt((int) (portfolio.cash() / (10 *bid)));

                    final var buyRequest = new SubmitOrderRequest.Buy(instrument.symbol(), UUID.randomUUID().toString(), qty, bid);
                    final var orderResponse = platform.submit(buyRequest);

                    logger.info("order {} placed with response {}", buyRequest, orderResponse);

                }
            }


            final var selectedElementForSell = portfolio
                    .portfolio()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.20 && pe.qty()>0)
                    .toList();

            for (final var element : selectedElementForSell) {
                final var history = platform.history(new HistoryRequest(element.instrument()));

                if (history instanceof HistoryResponse.History correct) {

                    final long orderSum = correct
                            .bought()
                            .stream()
                            .limit(averageCount)
                            .mapToLong(b->b.offer().price()*b.offer().qty())
                            .sum();
                    final long orderCount = correct
                            .bought()
                            .stream()
                            .limit(averageCount)
                            .mapToLong(b->b.offer().qty())
                            .sum();
                    if (orderCount == 0) continue;
                    final long ask = 1.1*orderSum/orderCount<minAsk ? minAsk:(long) (1.1*orderSum/orderCount);

                    final long qty = Math.min(element.qty(), minQty);

                    final var sellRequest = new SubmitOrderRequest.Sell(element.instrument().symbol(), UUID.randomUUID().toString(), qty, ask);
                    final var orderResponse = platform.submit(sellRequest);

                    logger.info("order {} placed with response {}", sellRequest, orderResponse);
                }
            }
        }
    }
}
