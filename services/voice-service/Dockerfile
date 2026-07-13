FROM python:3.11.11-slim-bookworm
RUN groupadd --system voicesecure && useradd --system --gid voicesecure --home /app voicesecure
WORKDIR /app
COPY pyproject.toml README.md ./
COPY src ./src
RUN python -m pip install --no-cache-dir .
USER voicesecure
ENV VOICE_HOST=0.0.0.0 VOICE_PORT=8090
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health/ready', timeout=2)"]
ENTRYPOINT ["python", "-m", "voice_service"]
