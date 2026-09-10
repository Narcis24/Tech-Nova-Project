#!/usr/bin/env python3
"""
Test yfinance connectivity locally (outside Docker)
Useful for debugging rate limiting and SSL certificate issues
"""

import yfinance as yf
import time
import csv
from datetime import datetime

# S&P 100 symbols
SP100_SYMBOLS = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'TSLA', 'META'
]

def test_single_symbol(symbol):
    """Test fetching price for a single symbol"""
    try:
        print(f"\n{'='*50}")
        print(f"Testing {symbol}...")
        print(f"{'='*50}")
        
        ticker = yf.Ticker(symbol)
        info = ticker.info
        
        print(f"✓ Successfully fetched data")
        print(f"  - Current Price: ${info.get('currentPrice', 'N/A')}")
        print(f"  - Regular Market Price: ${info.get('regularMarketPrice', 'N/A')}")
        print(f"  - Market Cap: {info.get('marketCap', 'N/A')}")
        print(f"  - PE Ratio: {info.get('trailingPE', 'N/A')}")
        print(f"  - Dividend Yield: {info.get('dividendYield', 'N/A')}")
        
        return {
            'symbol': symbol,
            'price': info.get('currentPrice') or info.get('regularMarketPrice'),
            'market_cap': info.get('marketCap'),
            'pe_ratio': info.get('trailingPE'),
            'dividend_yield': info.get('dividendYield'),
            'week_52_high': info.get('fiftyTwoWeekHigh'),
            'week_52_low': info.get('fiftyTwoWeekLow'),
            'timestamp': datetime.now().isoformat(),
            'status': 'SUCCESS'
        }
        
    except Exception as e:
        error_msg = str(e)
        print(f"✗ Error fetching {symbol}")
        print(f"  Error: {error_msg}")
        
        if '429' in error_msg or 'Too Many Requests' in error_msg:
            print(f"  ⚠️  RATE LIMITED - Yahoo Finance is blocking requests")
        elif 'SSL' in error_msg or 'certificate' in error_msg:
            print(f"  ⚠️  SSL/CERTIFICATE ERROR")
        elif 'Connection' in error_msg:
            print(f"  ⚠️  CONNECTION ERROR - Check your internet")
        
        return {
            'symbol': symbol,
            'status': 'FAILED',
            'error': error_msg,
            'timestamp': datetime.now().isoformat()
        }

def main():
    """Main test function"""
    print("\n" + "="*60)
    print("YFINANCE LOCAL TEST")
    print(f"Testing {len(SP100_SYMBOLS)} symbols...")
    print("="*60)
    
    results = []
    successful = 0
    failed = 0
    
    for i, symbol in enumerate(SP100_SYMBOLS, 1):
        print(f"\n[{i}/{len(SP100_SYMBOLS)}]", end=" ")
        
        # Longer delay between requests to avoid rate limiting
        if i > 1:
            wait_time = 3
            print(f"Waiting {wait_time}s before next request...")
            time.sleep(wait_time)
        
        result = test_single_symbol(symbol)
        results.append(result)
        
        if result['status'] == 'SUCCESS':
            successful += 1
        else:
            failed += 1
    
    # Print summary
    print(f"\n\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")
    print(f"✓ Successful: {successful}/{len(SP100_SYMBOLS)}")
    print(f"✗ Failed: {failed}/{len(SP100_SYMBOLS)}")
    
    # Save to CSV
    if successful > 0:
        csv_file = f"stock_prices_test_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv"
        try:
            with open(csv_file, 'w', newline='') as f:
                writer = csv.DictWriter(f, fieldnames=[
                    'symbol', 'price', 'market_cap', 'pe_ratio', 'dividend_yield',
                    'week_52_high', 'week_52_low', 'timestamp', 'status'
                ])
                writer.writeheader()
                for result in results:
                    if result['status'] == 'SUCCESS':
                        writer.writerow(result)
            
            print(f"\n✓ Saved successful prices to {csv_file}")
        except Exception as e:
            print(f"\n✗ Failed to save CSV: {e}")
    
    # Detailed error analysis
    if failed > 0:
        print(f"\n{'='*60}")
        print("ERROR DETAILS")
        print(f"{'='*60}")
        rate_limit_errors = 0
        ssl_errors = 0
        other_errors = 0
        
        for result in results:
            if result['status'] == 'FAILED':
                error = result.get('error', '')
                if '429' in error or 'Too Many Requests' in error:
                    rate_limit_errors += 1
                elif 'SSL' in error or 'certificate' in error:
                    ssl_errors += 1
                else:
                    other_errors += 1
        
        print(f"Rate Limiting (429): {rate_limit_errors}")
        print(f"SSL/Certificate Issues: {ssl_errors}")
        print(f"Other Errors: {other_errors}")
        
        if rate_limit_errors > 0:
            print(f"\n💡 SOLUTION: Yahoo Finance is rate-limiting.")
            print(f"   Try using mock data or increase delays between requests.")
        elif ssl_errors > 0:
            print(f"\n💡 SOLUTION: SSL certificate issue.")
            print(f"   Install: pip install certifi")
            print(f"   Or update certificates on your system.")

if __name__ == '__main__':
    main()
