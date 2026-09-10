package com.neueda.leap;

import yahoofinance.Stock;
import yahoofinance.YahooFinance;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches S&P 100 stocks from Yahoo Finance and exports to CSV
 */
public class SP100Fetcher {

    // S&P 100 stock symbols
    private static final String[] SP100_SYMBOLS = {
        "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "BERKB", "JPM", "JNJ",
        "BAC", "WMT", "XOM", "PG", "MA", "PEP", "HD", "LLY", "NVO", "ABBV",
        "KO", "COST", "CRM", "AVP", "CMG", "INTC", "VZ", "IBM", "TMO", "RTX",
        "NOW", "INTU", "ACN", "MCD", "HON", "NEE", "GOOG", "AON", "MSI", "AMGN",
        "PLD", "OTIS", "VRTX", "MU", "AZO", "CMCS", "QCOM", "MDLZ", "ELV", "SO",
        "DASH", "EL", "AMAT", "SYK", "ADBE", "DXCM", "AEP", "GILD", "MRK", "ASML",
        "ADSK", "SHW", "CPRT", "PKG", "CEG", "SNPS", "FCNCA", "PCAR", "REGN", "EA",
        "MAR", "CME", "EW", "RPM", "AXON", "THC", "TJX", "PSA", "MOH", "ROP",
        "SPLK", "CDNS", "PODD", "ANSS", "GLW", "INFO", "GEHC", "EXC", "IDXX",
        "WDAY", "ABNB", "MRNA", "NFLX", "CHTR", "ARM", "TEAM", "LOGI", "RXO", "PTC"
    };

    public static void main(String[] args) {
        System.out.println("Starting S&P 100 Stock Fetcher...");
        System.out.println("Total stocks to fetch: " + SP100_SYMBOLS.length);
        
        try {
            // Fetch stock data
            List<StockData> stockDataList = fetchStockData();
            
            // Export to CSV
            String filename = exportToCSV(stockDataList);
            
            System.out.println("✓ Successfully exported " + stockDataList.size() + " stocks to: " + filename);
            
        } catch (IOException e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Fetches stock data from Yahoo Finance with 30-second delay and retry logic with exponential backoff
     */
    private static List<StockData> fetchStockData() throws IOException {
        List<StockData> stockDataList = new ArrayList<>();
        
        System.out.println("\nFetching stock data from Yahoo Finance (30 sec delay per stock with retry logic)...");
        System.out.println("Retry strategy: Up to 3 attempts with exponential backoff (30s, 60s, 90s)\n");
        
        for (int i = 0; i < SP100_SYMBOLS.length; i++) {
            String symbol = SP100_SYMBOLS[i];
            
            // Fetch with retry logic
            StockData data = fetchStockWithRetry(symbol, i);
            
            if (data != null) {
                stockDataList.add(data);
                System.out.println("  [" + (i+1) + "/" + SP100_SYMBOLS.length + "] ✓ " + symbol + " - $" + data.price);
            } else {
                System.out.println("  [" + (i+1) + "/" + SP100_SYMBOLS.length + "] ✗ " + symbol + " - Failed after retries");
            }
            
            // 30 second delay between requests to avoid rate limiting
            if (i < SP100_SYMBOLS.length - 1) {
                System.out.println("  ⏳ Waiting 30s before next request...");
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    System.err.println("  ✗ Interrupted during delay");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        return stockDataList;
    }

    /**
     * Fetches a single stock with retry logic and exponential backoff
     */
    private static StockData fetchStockWithRetry(String symbol, int index) {
        int maxRetries = 3;
        long baseDelay = 30000; // 30 seconds
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Fetch individual stock
                Stock stock = YahooFinance.get(symbol);
                
                if (stock == null || stock.getQuote() == null || stock.getQuote().getPrice() == null) {
                    if (attempt < maxRetries) {
                        long waitTime = baseDelay * attempt;
                        System.out.println("    ⚠️  " + symbol + " - No data returned (attempt " + attempt + "/" + maxRetries + "). Retrying in " + (waitTime / 1000) + "s...");
                        Thread.sleep(waitTime);
                        continue;
                    }
                    return null;
                }
                
                return new StockData(
                    symbol,
                    stock.getName(),
                    stock.getQuote().getPrice(),
                    stock.getQuote().getChange(),
                    stock.getQuote().getChangeInPercent(),
                    stock.getQuote().getBid(),
                    stock.getQuote().getAsk(),
                    stock.getQuote().getVolume(),
                    LocalDateTime.now()
                );
                
            } catch (InterruptedException e) {
                System.err.println("    ✗ " + symbol + " - Interrupted");
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                String errorMsg = e.getMessage();
                
                // Check for rate limiting or crumb errors
                boolean isRateLimitError = errorMsg.contains("429") || errorMsg.contains("Too Many Requests") 
                    || errorMsg.contains("Unauthorized") || errorMsg.contains("Invalid Crumb");
                
                if (isRateLimitError && attempt < maxRetries) {
                    long waitTime = baseDelay * attempt;
                    System.out.println("    ⚠️  " + symbol + " - Rate limited/Crumb error (attempt " + attempt + "/" + maxRetries + "). Retrying in " + (waitTime / 1000) + "s...");
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else if (attempt < maxRetries) {
                    long waitTime = baseDelay * attempt;
                    System.out.println("    ⚠️  " + symbol + " - Error (attempt " + attempt + "/" + maxRetries + "): " + errorMsg.substring(0, Math.min(50, errorMsg.length())));
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    System.err.println("    ✗ " + symbol + " - Error after " + maxRetries + " attempts: " + errorMsg);
                    return null;
                }
            }
        }
        
        return null;
    }

    /**
     * Exports stock data to CSV file
     */
    private static String exportToCSV(List<StockData> stockDataList) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "sp100_stocks_" + timestamp + ".csv";
        
        System.out.println("\nWriting to CSV file: " + filename);
        
        try (FileWriter out = new FileWriter(filename);
             CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT
                 .withHeader("Symbol", "Company Name", "Price", "Change", "Change %", "Bid", "Ask", "Volume", "Timestamp"))) {
            
            for (StockData data : stockDataList) {
                printer.printRecord(
                    data.symbol,
                    data.name,
                    data.price,
                    data.change,
                    data.changePercent,
                    data.bid,
                    data.ask,
                    data.volume,
                    data.timestamp
                );
            }
        }
        
        return filename;
    }

    /**
     * Inner class to hold stock data
     */
    private static class StockData {
        String symbol;
        String name;
        BigDecimal price;
        BigDecimal change;
        BigDecimal changePercent;
        BigDecimal bid;
        BigDecimal ask;
        Long volume;
        LocalDateTime timestamp;

        StockData(String symbol, String name, BigDecimal price, BigDecimal change,
                  BigDecimal changePercent, BigDecimal bid, BigDecimal ask, Long volume,
                  LocalDateTime timestamp) {
            this.symbol = symbol;
            this.name = name;
            this.price = price;
            this.change = change;
            this.changePercent = changePercent;
            this.bid = bid;
            this.ask = ask;
            this.volume = volume;
            this.timestamp = timestamp;
        }
    }
}
