package com.example;

import com.example.model.rest.*;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.example.platform.Platform;
import com.example.strategy.InstrumentDestroyer;
import com.example.strategy.MoneyCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TradingBot {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Logger logger = LoggerFactory.getLogger(TradingBot.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        logger.info("Starting the application");

        try (final var credentialsResource = TradingBot.class.getResourceAsStream("/credentials/player1.json")) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var marketPlugin = new Hackathon(credentials);
                final var ordersController = new MoneyCollector(marketPlugin);

                final var beeperHandle = scheduler.scheduleAtFixedRate(ordersController, 1, 15, SECONDS);
        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }


}
