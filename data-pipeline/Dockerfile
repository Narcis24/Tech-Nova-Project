FROM python:3.12-slim
# yfinance keeps a cache in the user's home, so the non-root user gets one
RUN apt-get update \
 && apt-get install -y --only-upgrade libpcre2-8-0 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home pipeline
WORKDIR /pipeline
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY load.py .
USER pipeline
ENTRYPOINT ["python", "load.py"]
