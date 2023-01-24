package com.example.strategy;

import com.example.model.order.ProcessedOrder;
import com.example.model.rest.*;
import com.example.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

public class InstrumentDestroyer implements Runnable {

    private static final long minQty = 10;
    private static final long  maxQty = 100;
    private static final long minAsk = 60;
    private static final long minBid = 79;

    private static final int limitForAverageSell = 100;
    private static final int limitForAverageBuy = 100;
    private static final long minCashForBuy = 20000;
    private static final Logger logger = LoggerFactory.getLogger(InstrumentDestroyer.class);

    private final Platform platform;

    private static final Random rg = new Random();

    public InstrumentDestroyer(Platform platform) {
        this.platform = platform;
    }
    public record OfferStat(long qty,long price){}

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
            logger.info("My money {}",portfolio.cash());

            final var selectedForBuy = instruments
                    .available()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.10)
                    .toList();

            //final var selectedForBuy = instruments
            //        .available()
            //        .stream()
            //        .filter(pe->pe.symbol().equals("SADOVAYA")).toList();
            if(portfolio.cash()>minCashForBuy)
                for (final var instrument : selectedForBuy) {
                    final var history = platform.history(new HistoryRequest(instrument));

                    if (history instanceof HistoryResponse.History correct) {
                        //logger.info("Try Buy {}:",instrument.symbol());
                        final long bid;
                        if(correct.sold().size() == 0 )
                            bid = minBid;
                        else{
                            final long average = trimmedAverage(correct.sold()
                                    .stream()
                                    .limit(limitForAverageBuy)
                                    .map(x-> new OfferStat(x.offer().qty(), x.offer().price())).toList());
                            bid = (long) (1.1*average);
                        }
                        final var qty = 1+Math.min((long) rg.nextInt(1+ (int) (portfolio.cash() / (10 *bid))),maxQty);
                        final var buyRequest = new SubmitOrderRequest.Buy(instrument.symbol(), UUID.randomUUID().toString(), qty, bid);
                        final var orderResponse = platform.submit(buyRequest);

                        logger.info("{}: (symbol={} qty={} bid={}) -> {}",
                                buyRequest.getClass().getSimpleName(),buyRequest.symbol(),buyRequest.qty(),buyRequest.bid(), orderResponse.getClass().getSimpleName());
                    }
                }


            final var selectedForSell = portfolio
                    .portfolio()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.25 && pe.qty()>0)
                    .toList();

            for (final var element : selectedForSell) {

                final var history = platform.history(new HistoryRequest(element.instrument()));

                if (history instanceof HistoryResponse.History correct) {
                    //logger.info("Try Sell {}:",element.instrument().symbol());
                    final long ask;
                    if(correct.bought().size() == 0){
                        ask = minAsk;
                    }else{
                        final long average = trimmedAverage(correct.bought()
                                .stream()
                                .limit(limitForAverageSell)
                                .map(x-> new OfferStat(x.offer().qty(), x.offer().price())).toList());
                        ask = (long) (0.9*average);
                    }

                    final long qty = Math.min(element.qty(), minQty);

                    final var sellRequest = new SubmitOrderRequest.Sell(element.instrument().symbol(), UUID.randomUUID().toString(), qty, ask);
                    final var orderResponse = platform.submit(sellRequest);

                    logger.info("{}: (symbol={} qty={} ask={}) -> {}",
                            sellRequest.getClass().getSimpleName(),sellRequest.symbol(),sellRequest.qty(),sellRequest.ask(), orderResponse.getClass().getSimpleName());

                }
            }
        }
    }

    public static long trimmedAverage(List<OfferStat> ordersList){
        OfferStat[] orders = ordersList.toArray(OfferStat[]::new);
        Arrays.sort(orders,Comparator.comparingLong(x->x.price));



        List<OfferStat> forRemove = new LinkedList<>();


        final long count = Arrays.stream(orders).mapToLong(a->a.qty).sum();
        final long countRemove = (long) (0.1*count);
        long minCount = countRemove,maxCountBreak = count-2*countRemove;

        for(OfferStat x:orders){
            if(minCount>0){
                if(x.qty <= minCount){
                    minCount -= x.qty;
                    forRemove.add(x);
                }else{
                    forRemove.add(new OfferStat(minCount, x.price));
                    minCount = 0;
                }
            } else{
                if(maxCountBreak == 0)
                    forRemove.add(x);
                else {
                    if (x.qty <= maxCountBreak) {
                        maxCountBreak -= x.qty;
                    }else{
                        forRemove.add(new OfferStat(x.qty - maxCountBreak, x.price));
                        maxCountBreak = 0;
                    }
                }
            }
        }

        return (Arrays.stream(orders).mapToLong(x->x.qty*x.price).sum()-
                forRemove.stream().mapToLong(x->x.price*x.qty).sum() )/(count-2*countRemove);
    }
}
