# Use an official Python runtime as a parent image
FROM python:3.10-slim

# Set the working directory in the container
WORKDIR /app

# Copy only the files required to install and run the service.
COPY pyproject.toml app.py ./
COPY cinnamon_text_anonymization ./cinnamon_text_anonymization

# Install the application and dependencies declared in pyproject.toml.
RUN pip install --upgrade pip && pip install --no-cache-dir .

# Make port 5060 available to the world outside this container
EXPOSE 5060

# Define environment variable
ENV FLASK_APP=app.py

# Run the application using Gunicorn when the container launches, timeout is set to one day
CMD ["sh", "-c", "exec gunicorn --workers=4 --bind=0.0.0.0:5060 --timeout=86400 app:app"]
