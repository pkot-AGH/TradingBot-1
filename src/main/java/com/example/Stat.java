package com.example;

import com.example.model.order.Client;
import com.example.model.order.Instrument;
import com.example.model.order.ProcessedOrder;
import com.example.model.order.SubmittedOrder;
import com.example.model.rest.*;
import com.example.model.security.Credentials;
import com.example.platform.Hackathon;
import com.example.platform.Platform;
import com.example.strategy.MoneyCollector;
import com.example.strategy.ThreeCandles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
    public static void main(String[] args) {
        logger.info("Starting the application");
        final String credentialsStr;
        if(args.length <2 ){
            logger.info("enter the player and type of stat");
            return;
        }
        if(args[0].equals("player1"))
            credentialsStr =  "/credentials/player1.json";
        else
            credentialsStr =  "/credentials/player2.json";

        try (final var credentialsResource = Stat.class.getResourceAsStream(credentialsStr)) {
            final var credentials = objectMapper.readValue(credentialsResource, Credentials.class);
            final var platform = new Hackathon(credentials);
            switch(args[1]){
                case "csv"-> historyToCSV(platform);
                case "flow"-> flow(platform,credentials.client());
                default ->info(platform,credentials.client());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void historyToCSV(Platform platform){
        final var fetchedInstruments = platform.instruments();
        File csvOutputFile = new File("history.csv");
        try(PrintWriter pw = new PrintWriter(csvOutputFile)) {
            pw.println("Date,Client,Operation,Instrument,Qty,Price");
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
                                        -z.offer().qty() + "," +
                                        z.offer().price()
                        ));

                    }
                });
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void flow(Platform platform, Client client){
        final var fetchedInstruments = platform.instruments();
        final var fetchedPortfolio = platform.portfolio();
        if (fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio &&
                fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
            //final var fetchedProcessed = platform.processed();
            //final var fetchedSubmited = platform.submitted();
            Map<String,Long> instrumentSummary = portfolio.portfolio().stream()
                    .collect(Collectors.toMap(p->p.instrument().symbol(),p->p.qty()));
            long totalQty = 0,totalPrice = 0;
            for(Instrument instrument:instruments.available()) {
                final var fetchedHistory = platform.history(new HistoryRequest(instrument));
                if (fetchedHistory instanceof HistoryResponse.History history) {
                    long qty = 0, price = 0;
                    for (ProcessedOrder.Bought x : history.bought().stream()
                            .filter(x -> x.identifier().client().name().equals(client.name())).toList()) {
                        qty += x.offer().qty();
                        price += x.offer().qty() * x.offer().price();
                    }
                    for (ProcessedOrder.Sold x : history.sold().stream()
                            .filter(x -> x.identifier().client().name().equals(client.name())).toList()) {
                        qty -= x.offer().qty();
                        price -= x.offer().qty() * x.offer().price();
                    }
                    System.out.println(client.name() +
                            ": Instrument: "+ instrument.symbol()+
                            " Current qty: "+instrumentSummary.getOrDefault(instrument.symbol(),0L)+
                            " Qty flow: " + qty +
                            " Price flow: " + price);
                    totalQty += qty;
                    totalPrice += price;
                }
            }
            System.out.println(client.name()+
                    ": Cash: "+portfolio.cash()+
                    " Total Qty flow: " + totalQty+
                    " Total Price flow: " + totalPrice);

        }
    }
    public static void info(Platform platform,Client client)  {
        for(int i=0;i<1000;i++) {
            final var fetchedPortfolio = platform.portfolio();
            final var fetchedInstruments = platform.instruments();
            if (fetchedPortfolio instanceof PortfolioResponse.Portfolio porfolio &&
                    fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {

                Map<String, Long> instrumentsQtyMap = porfolio.portfolio().stream()
                        .collect(Collectors.toMap(p -> p.instrument().symbol(), p -> p.qty()));
                String instrumentsAvailableQtyStr = instruments.available().stream()
                        .map(x -> String.valueOf(instrumentsQtyMap.getOrDefault(x.symbol(), 0L)))
                        .reduce((a, b) -> a + "," + b).orElse("");

                System.out.println(client.name() + " Cash: " + porfolio.cash() + " Instruments: " + instrumentsAvailableQtyStr);
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

    }

}
