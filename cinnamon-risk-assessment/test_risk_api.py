import os.path
import time

import requests
import json

# Base URL of the FastAPI server
base_url = "http://localhost:8000"


# Function to test starting a risk assessment (POST request)
def test_start_risk_assessment(process_id):
    url = f"{base_url}/risk_assessments/{process_id}"
    files = {
        "callback_url": (None, "http://localhost:8000/callback"),
        "attribute_config": open("inputs/attribute_config_with_DateFormat.yaml", "rb"),
        "risk_assessment_config": open("inputs/input_user_anonymeter.yaml", "rb"),
        "original_data": open("inputs/heart.csv", "rb"),
        "synthetic_data": open("inputs/TVAE/tvae_synthetic_data.csv", "rb"),
        # Optionally include holdout_data if available
        "holdout_data": open("inputs/TVAE/tvae_test.csv", "rb") if os.path.exists(
            "inputs/TVAE/tvae_test.csv") else None,
    }

    response = requests.post(url, files=files)
    print("Start Risk Assessment Response:", response.status_code, response.json())


# Function to test canceling a risk assessment (DELETE request)
def test_cancel_risk_assessment(process_id):
    url = f"{base_url}/risk_assessments/{process_id}"
    response = requests.delete(url)
    print("Cancel Risk Assessment Response:", response.status_code, response.json())


def test_start_base_assessment(process_id):
    url = f"{base_url}/base_assessments/{process_id}"
    files = {
        "callback_url": (None, "http://localhost:8000/callback"),
        "attribute_config": open("inputs/attribute_config_with_DateFormat.yaml", "rb"),
        "risk_assessment_config": open("inputs/input_user_anonymeter.yaml", "rb"),
        "original_data": open("inputs/heart.csv", "rb")
    }

    response = requests.post(url, files=files)
    print("Start Base Assessment Response:", response.status_code, response.json())


# Function to test canceling a risk assessment (DELETE request)
def test_cancel_base_assessment(process_id):
    url = f"{base_url}/base_assessments/{process_id}"
    response = requests.delete(url)
    print("Cancel Base Assessment Response:", response.status_code, response.json())


# Replace with the actual process ID you'd like to test
process_id = 123

# Run tests
for n in range(0, 10):
    test_start_risk_assessment(f"{process_id + n}_task")

time.sleep(60)

for n in range(0, 10):
    test_cancel_risk_assessment(f"{process_id + n}_task")

process_id = 224

for n in range(0, 10):
    test_start_base_assessment(f"{process_id + n}_task")

time.sleep(60)

for n in range(0, 10):
    test_cancel_base_assessment(f"{process_id + n}_task")
