FROM python:3.10-slim

WORKDIR /app

COPY pyproject.toml app.py ./
RUN pip install --no-cache-dir .

EXPOSE 5070

CMD ["sh", "-c", "exec gunicorn --workers=2 --bind=0.0.0.0:5070 --timeout ${TEXT_EXTRACTION_REQUEST_TIMEOUT_SECONDS:-21600} app:app"]
