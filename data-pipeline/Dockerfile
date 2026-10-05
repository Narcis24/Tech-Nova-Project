FROM python:3.12-slim
RUN apt-get update \
 && apt-get install -y --only-upgrade libpcre2-8-0 \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /pipeline
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
ENTRYPOINT ["python", "load.py"]
