package com.example;

import com.example.model.rest.*;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.example.strategy.InstrumentDestroyer;
import com.example.strategy.MoneyCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

public class TradingBot {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Logger logger = LoggerFactory.getLogger(TradingBot.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static void main(String[] args){
        logger.info("Starting the application");
        try (final var credentialsResource = TradingBot.class.getResourceAsStream("/credentials/player2.json")) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var marketPlugin = new Hackathon(credentials);

            final var ordersController = new MoneyCollector(marketPlugin);

            final var beeperHandle = scheduler.scheduleAtFixedRate(ordersController, 1, 5, SECONDS);
        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }
    public static void main2(String[] args) {
        logger.info("Starting the application");
        try (final var credentialsResource = TradingBot.class.getResourceAsStream("/credentials/player2.json")) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var platform= new Hackathon(credentials);
            final var fetchetedInstruments = platform.instruments();
            final  var fetchedPortfolio = platform.portfolio();
            if(fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio &&
                    fetchetedInstruments instanceof InstrumentsResponse.Instruments instruments){
                for(final var element: portfolio.portfolio()){
                    final  var instrument = element.instrument();
                    //logger.info("instrument {}",instrument);
                }
                final var selectForBuy = instruments.available().stream().findFirst().get();
                final var buyRequest = new SubmitOrderRequest.Buy(selectForBuy.symbol(), UUID.randomUUID().toString(),27,93);
                //final var orderResponse = platform.submit(buyRequest);
                final var fetchedProcessed = platform.processed();
                final var fetchedSubmited = platform.submitted();
                //final var history = platform.history(new HistoryRequest(selectForBuy));
                if(fetchedProcessed instanceof ProcessedResponse.Processed processed){
                    for(final var process:processed.bought())
                        logger.info("{}",process);
                    for(final var process:processed.sold())
                        logger.info("{}",process);
                    for(final var process:processed.expired())
                        logger.info("{}",process);
                }
                if(fetchedSubmited instanceof SubmittedResponse.Submitted submited){
                    for(final var buy:submited.buy())
                        logger.info("{}",buy);
                    for(final var sell:submited.sell())
                        logger.info("{}",sell);
                }
                //if(history instanceof HistoryResponse.History correct){
                //    //for(final var bought:correct.bought())
                //    //   logger.info("instrument {} bought {}",selectForBuy,bought);
                //    for(final var bought:correct.bought())
                //        logger.info("{}",bought);
                //    for(final var sold:correct.sold())
                //        logger.info("{}",sold);
                //}

            }

//            final var unicorn = new TradingUnicorn();
//
//            Executors.newScheduledThreadPool(1);

        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }
}
