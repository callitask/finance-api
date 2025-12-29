import sys
import os
import logging
import yfinance as yf
import pandas as pd
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta, date
import math
from decimal import Decimal, getcontext, ROUND_HALF_UP

# --- CONFIGURATION: Financial Precision ---
getcontext().prec = 28
# Rounding strategy for currency
DECIMAL_CTX = Decimal("0.00000001")

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] [MarketData] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)

TICKERS = [
    "^GSPC", "^DJI", "^IXIC", "^RUT", "^VIX", "^NYA", "^GDAXI", "^FTSE", "^FCHI", "^IBEX", "^STOXX50E",
    "^NSEI", "^BSESN", "^NSEBANK", "^CNXIT", "^HSI", "^N225", "^STI", "000001.SS",
    "GC=F", "SI=F", "CL=F", "NG=F", "HG=F",
    "USDINR=X", "EURINR=X", "JPYINR=X", "GBPINR=X", "AUDINR=X", "EURUSD=X",
    "BTC-INR", "ETH-INR", "SOL-INR", "XRP-INR", "DOGE-INR"
]

def get_db_engine():
    # SECURITY: Read from Environment Variables (Invisible to Process List)
    jdbc_url = os.getenv("DB_URL", "")
    user = os.getenv("DB_USER", "")
    password = os.getenv("DB_PASSWORD", "")

    if not jdbc_url or not user or not password:
        # Fallback to args only if env vars are missing (Backward Compatibility)
        if len(sys.argv) >= 4:
            jdbc_url = sys.argv[1]
            user = sys.argv[2]
            password = sys.argv[3]
        else:
            logging.error("Fatal: Database credentials missing from ENV and ARGS.")
            sys.exit(1)

    try:
        clean_url = jdbc_url.replace("jdbc:", "")
        if "mariadb://" in clean_url:
            clean_url = clean_url.replace("mariadb://", "mysql+mysqlconnector://")
        elif "mysql://" in clean_url:
            clean_url = clean_url.replace("mysql://", "mysql+mysqlconnector://")

        if "?" in clean_url:
            clean_url = clean_url.split("?")[0]

        # Securely construct connection string
        connection_str = f"{clean_url.replace('//', f'//{user}:{password}@')}"
        return create_engine(connection_str)
    except Exception as e:
        logging.error(f"Fatal: Could not parse DB URL. Error: {e}")
        sys.exit(1)

def to_decimal(val):
    """Safely convert numpy/float/string to Decimal for DB storage"""
    if val is None: return None
    if isinstance(val, (float, int)):
        if math.isnan(val) or math.isinf(val): return None
    if pd.isna(val): return None
    
    try:
        # Convert to string first to avoid float precision artifacts
        d = Decimal(str(val))
        return d.quantize(DECIMAL_CTX, rounding=ROUND_HALF_UP)
    except:
        return None

def get_last_db_date(conn, ticker):
    try:
        query = text("SELECT MAX(price_date) FROM historical_price WHERE ticker = :ticker")
        result = conn.execute(query, {"ticker": ticker}).fetchone()
        if result and result[0]:
            return result[0]
    except Exception as e:
        logging.warning(f"[{ticker}] Could not fetch last date from DB: {e}")
    return None

def process_ticker(ticker, engine):
    try:
        with engine.connect() as conn:
            last_date = get_last_db_date(conn, ticker)

            # --- SMART SYNC LOGIC ---
            if last_date is None:
                logging.info(f"[{ticker}] Status: NEW. Strategy: FULL HISTORY FETCH.")
                hist = yf.Ticker(ticker).history(period="max", auto_adjust=False)
            else:
                # Fetch overlap to calculate changes correctly
                start_fetch = last_date - timedelta(days=7)
                logging.info(f"[{ticker}] Status: ACTIVE. Last Date: {last_date}. Strategy: SYNC from {start_fetch}.")
                hist = yf.Ticker(ticker).history(start=start_fetch, auto_adjust=False)

            if hist.empty:
                logging.warning(f"[{ticker}] API returned no data. Skipping.")
                return

            latest_row = hist.iloc[-1]
            current_price = to_decimal(latest_row.get('Close'))

            prev_close = None
            if len(hist) >= 2:
                prev_row = hist.iloc[-2]
                prev_close = to_decimal(prev_row.get('Close'))

            change_amt = None
            change_pct = None
            
            # --- PRECISE CALCULATION ---
            if current_price is not None and prev_close is not None and prev_close != Decimal(0):
                change_amt = current_price - prev_close
                change_pct = (change_amt / prev_close) * Decimal(100)

            currency = "INR" if "INR" in ticker else "USD"

            quote_sql = text("""
                INSERT INTO quote_data
                (ticker, name, currency, current_price, change_amount, change_percent,
                 previous_close, day_high, day_low, volume, last_updated)
                VALUES (:t, :n, :curr, :cp, :ca, :cpct, :pc, :dh, :dl, :vol, NOW())
                ON DUPLICATE KEY UPDATE
                current_price=:cp, change_amount=:ca, change_percent=:cpct,
                previous_close=:pc, day_high=:dh, day_low=:dl, volume=:vol, last_updated=NOW()
            """)

            conn.execute(quote_sql, {
                "t": ticker, "n": ticker, "curr": currency, "cp": current_price,
                "ca": change_amt, "cpct": change_pct, "pc": prev_close,
                "dh": to_decimal(latest_row.get('High')),
                "dl": to_decimal(latest_row.get('Low')),
                "vol": int(latest_row.get('Volume', 0))
            })
            conn.commit()

            for dt, row in hist.iterrows():
                record_date = dt.date()
                close_p = to_decimal(row.get('Close'))
                if close_p is not None:
                    conn.execute(text("""
                        INSERT INTO historical_price (ticker, price_date, close_price)
                        VALUES (:t, :d, :p)
                        ON DUPLICATE KEY UPDATE close_price = :p
                    """), {"t": ticker, "d": record_date, "p": close_p})

            conn.commit()
            logging.info(f"[{ticker}] Sync successful. History updated.")

    except Exception as e:
        logging.error(f"[{ticker}] FAILED. Reason: {e}")

def main():
    logging.info(f"Initializing Market Data Engine. Target Tickers: {len(TICKERS)}")

    try:
        engine = get_db_engine()
        for ticker in TICKERS:
            process_ticker(ticker, engine)
        logging.info("Global Market Data Sync Completed Successfully.")

    except Exception as e:
        logging.critical(f"Engine Crash: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()