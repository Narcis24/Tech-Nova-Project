FROM python:3.12-slim AS runtime
RUN apt-get update \
 && apt-get install -y --only-upgrade libpcre2-8-0 \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /pipeline
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
ENTRYPOINT ["python", "load.py"]

# docker build --target test: the same image plus pytest; tests/run.sh uses it
FROM runtime AS test
COPY requirements-dev.txt .
RUN pip install --no-cache-dir -r requirements-dev.txt
ENTRYPOINT ["python", "-m", "pytest", "-q", "tests"]

# a plain docker build stays the runtime image
FROM runtime
