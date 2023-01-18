package com.example;

import com.example.model.rest.*;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TradingBot {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Logger logger = LoggerFactory.getLogger(TradingBot.class);

    public static void main(String[] args) {
        logger.info("Starting the application");
        try (final var credentialsResource = TradingBot.class.getResourceAsStream("/credentials/player1.json")) {
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
                final var buyRequest = new SubmitOrderRequest.Buy(selectForBuy.symbol(), UUID.randomUUID().toString(),1,93);
                //final var orderResponse = platform.submit(buyRequest);
                final var history = platform.history(new HistoryRequest(selectForBuy));
                if(history instanceof HistoryResponse.History correct){
                    //for(final var bought:correct.bought())
                    //   logger.info("instrument {} bought {}",selectForBuy,bought);
                    for(final var bought:correct.bought())
                        logger.info("{}",bought);
                    for(final var sold:correct.sold())
                        logger.info("{}",sold);
                }

            }

//            final var unicorn = new TradingUnicorn();
//
//            Executors.newScheduledThreadPool(1);

        } catch (Exception exception) {
            logger.error("Something bad happened", exception);
        }
    }
}
