package com.example;

import com.example.model.order.Instrument;
import com.example.model.order.SubmittedOrder;
import com.example.model.rest.*;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.example.strategy.MoneyCollector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.*;

public class Stat {
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final Logger logger = LoggerFactory.getLogger(Stat.class);

    public record StatSummary(long totalQty, long totalPrice){};
    public record InstrumentStatSummary(String symbol,long currentQty, long totalQty, long totalPrice){};
    public static void main(String[] args) {
        logger.info("Starting the application");
        final String credentialsStr;
        if(args.length == 0 || args[0].equals("player1"))
            credentialsStr =  "/credentials/player1.json";
        else
            credentialsStr =  "/credentials/player2.json";
        try (final var credentialsResource = Stat.class.getResourceAsStream(credentialsStr)) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var platform = new Hackathon(credentials);
            final var fetchedInstruments = platform.instruments();
            final var fetchedPortfolio = platform.portfolio();
            if (fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio &&
                    fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
                final var fetchedProcessed = platform.processed();
                final var fetchedSubmited = platform.submitted();

                if (fetchedSubmited instanceof SubmittedResponse.Submitted submitted) {
                    Map<Instrument,StatSummary> instrumentBoughtSummary =  submitted
                            .buy()
                            .stream()
                            .collect(groupingBy(SubmittedOrder.Buy::instrument,collectingAndThen(toList(), list->{
                                    long qty=0,total = 0;
                                    for(final var e:list) {
                                        qty += e.bid().qty();
                                        total += e.bid().qty() * e.bid().price();
                                    }
                                    return new StatSummary(qty,total);
                            })));
                    Map<Instrument,StatSummary> instrumentSoldSummary =  submitted
                            .sell()
                            .stream()
                            .collect(groupingBy(SubmittedOrder.Sell::instrument,collectingAndThen(toList(), list->{
                                long qty=0,total = 0;
                                for(final var e:list) {
                                    qty += e.ask().qty();
                                    total += e.ask().qty() * e.ask().price();
                                }
                                return new StatSummary(qty,total);
                            })));
                    final StatSummary zero = new StatSummary(0,0);
                    List<InstrumentStatSummary> instrumentSummary = portfolio.portfolio().stream().map(e -> {
                        return new InstrumentStatSummary(e.instrument().symbol(),
                                e.qty(),
                                instrumentBoughtSummary.getOrDefault(e.instrument(),zero).totalQty
                                        -instrumentSoldSummary.getOrDefault(e.instrument(),zero).totalQty(),
                                instrumentBoughtSummary.getOrDefault(e.instrument(),zero).totalPrice()
                                        -instrumentSoldSummary.getOrDefault(e.instrument(),zero).totalPrice());
                    }).toList();

                    instrumentSummary.forEach(x->logger.info("Instrument: {} Current qty:{}  Total qty flow:{} Total price flow: {} ",x.symbol,x.currentQty,x.totalQty,x.totalPrice));
                    logger.info("My cash: {}", portfolio.cash());
                    logger.info("Cash flow through instruments {}",instrumentSummary.stream().mapToLong(e->e.totalPrice).sum());
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
