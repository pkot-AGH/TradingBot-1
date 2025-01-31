package com.example;

import com.example.model.rest.*;
import com.example.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class OrdersController implements Runnable {

    private static final long minQty = 10;
    private static final long minAsk = 13;
    private static final long minBid = 79;

    private static final Logger logger = LoggerFactory.getLogger(OrdersController.class);

    private final Platform platform;

    private static final Random rg = new Random();

    public OrdersController(Platform platform) {
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
            for (final var element : portfolio.portfolio()) {
                final var instrument = element.instrument();
                final var orders = platform.history(new HistoryRequest(instrument));
                logger.info("instrument {} has orders {}", instrument, orders);
            }

            final var selectedForBuy = instruments
                    .available()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.10)
                    .toList();

            for (final var instrument : selectedForBuy) {
                final var history = platform.history(new HistoryRequest(instrument));

                if (history instanceof HistoryResponse.History correct) {
                    final long bid = (long) (
                            1.1 * correct
                                    .bought()
                                    .stream()
                                    .mapToLong(b -> b.offer().price())
                                    .average()
                                    .orElse(minBid)
                    );

                    final var qty = rg.nextInt((int) (portfolio.cash() / 4 / bid));
                    final var buyRequest = new SubmitOrderRequest.Buy(instrument.symbol(), UUID.randomUUID().toString(), qty, bid);
                    final var orderResponse = platform.submit(buyRequest);

                    logger.info("order {} placed with response {}", buyRequest, orderResponse);
                }
            }


            final var selectedElementForSell = portfolio
                    .portfolio()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.20)
                    .toList();

            for (final var element : selectedElementForSell) {
                final var history = platform.history(new HistoryRequest(element.instrument()));

                if (history instanceof HistoryResponse.History correct) {
                    final long ask = (long) (
                            0.9 * correct
                                    .bought()
                                    .stream()
                                    .mapToLong(b -> b.offer().price())
                                    .average()
                                    .orElse(minAsk)
                    );

                    final long qty = Math.min(element.qty(), minQty);
                    final var sellRequest = new SubmitOrderRequest.Sell(element.instrument().symbol(), UUID.randomUUID().toString(), qty, ask);
                    final var orderResponse = platform.submit(sellRequest);

                    logger.info("order {} placed with response {}", sellRequest, orderResponse);
                }
            }
        }
    }
}
