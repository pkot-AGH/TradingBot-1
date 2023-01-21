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

            if(args.length == 0) {
                final var ordersController = new MoneyCollector(marketPlugin);

                final var beeperHandle = scheduler.scheduleAtFixedRate(ordersController, 1, 5, SECONDS);
            }else
                get_history(marketPlugin);
        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }
    public static void get_history(Platform platform){
         final var fetchedInstruments = platform.instruments();
         File csvOutputFile = new File("history.csv");
         try(PrintWriter pw = new PrintWriter(csvOutputFile)) {
             if (fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
                 instruments.available().forEach(x -> {
                     final var fetchedHistory = platform.history(new HistoryRequest(x));
                     if (fetchedHistory instanceof HistoryResponse.History history) {
                         history.bought().forEach(z -> pw.println(
                                 z.created().toString() + "," +
                                         z.identifier().client().name() + "," +
                                         "bought" + "," +
                                         z.instrument().symbol() + "," +
                                         z.offer().qty() + "," +
                                         z.offer().price()
                         ));
                         history.sold().forEach(z -> pw.println(
                                 z.created().toString() + "," +
                                         z.identifier().client().name() + "," +
                                         "sold" + "," +
                                         z.instrument().symbol() + "," +
                                         z.offer().qty() + "," +
                                         z.offer().price()
                         ));

                     }
                 });
             }
         }catch(IOException e){
             e.printStackTrace();
         }
    }

}
