# Use an official Python runtime as a parent image
FROM python:3.10-slim

# Set the working directory in the container
WORKDIR /app

# Copy the requirements file into the working directory
COPY requirements.txt ./

# Install libgomp for LightGBM
RUN apt-get update && apt-get install -y libgomp1

# Install any needed packages specified in requirements.txt
RUN pip install --upgrade pip && pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application code
COPY . .

# Make port 5010 available to the world outside this container
EXPOSE 5010

# Define environment variable
ENV FLASK_APP=app.py

# Run the application using Gunicorn when the container launches, timeout is set to one day
CMD ["gunicorn", "--workers=4", "--bind=0.0.0.0:5010", "--timeout=86400", "app:app"]



