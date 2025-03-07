# Use an official Python runtime as a parent image
FROM python:3.10-slim

# Set the working directory in the container
WORKDIR /app

# Copy only the requirements file first for caching
COPY requirements.txt ./

# Install dependencies
RUN pip install --upgrade pip && pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application code
COPY . .

# Add the working directory to PYTHONPATH
ENV PYTHONPATH="${PYTHONPATH}:/app"

# Expose the desired port
EXPOSE 8000

# Run the application
CMD ["gunicorn", "--workers=4", "--worker-class=uvicorn.workers.UvicornWorker", "--bind=0.0.0.0:8000", "--timeout=86400", "main:app"]

