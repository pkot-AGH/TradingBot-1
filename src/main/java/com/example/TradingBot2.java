package com.example;

import com.example.model.rest.HistoryRequest;
import com.example.model.rest.HistoryResponse;
import com.example.model.rest.InstrumentsResponse;
import com.example.model.rest.PortfolioResponse;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.example.strategy.InstrumentDestroyer;
import com.example.strategy.MoneyCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TradingBot2 {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Logger logger = LoggerFactory.getLogger(TradingBot2.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        logger.info("Starting the application");
        try (final var credentialsResource = TradingBot2.class.getResourceAsStream("/credentials/player2.json")) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var marketPlugin = new Hackathon(credentials);

            final var ordersController = new InstrumentDestroyer(marketPlugin);

            final var beeperHandle = scheduler.scheduleAtFixedRate(ordersController, 1, 5, SECONDS);
        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }

}
