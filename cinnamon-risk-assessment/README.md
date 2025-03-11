# FastAPI Risk Assessment API
This repository contains a FastAPI application to perform risk assessments and a script for testing the API.

## Prerequisites
Python (3.8+ recommended)
FastAPI and other required packages (see installation instructions below)
Gunicorn to run the application
requests library for the testing script

## Installation
### Clone the repository:

    git clone <repository_url>
    cd <repository_folder>

### Install required Python packages:

    pip install -r requirements.txt

Ensure that the following files are available:
* attribute_config.yaml - YAML configuration file for attribute settings.
* input_user_anonymeter.yaml - YAML configuration file for risk assessment.
* original_data.csv, synthetic_data.csv, holdout_data.csv - CSV files for data (place in the root directory or update paths in test_api.py).

## Running the Application with Docker:
 ```
docker build -t cinnamon-risk-assessment .
docker run -d --name cinnamon-risk-assessment -p 8000:8000 cinnamon-risk-assessment
 ```

## Running the Application with Gunicorn
To start the FastAPI application using Gunicorn:

1. Run the server:
    ``` 
   gunicorn -w 4 -k uvicorn.workers.UvicornWorker main:app
   ```
    * ```-w 4``` specifies the number of worker processes (adjust based on server capacity).
    * ```-k uvicorn.workers.UvicornWorker``` tells Gunicorn to use Uvicorn as the worker class.
    * ```main:app``` points to the FastAPI application (```app``` instance in main.py).

   On Windows machines, use uvicorn directly via:
   ``` 
   uvicorn main:app --reload
   ```

2. The application should now be running at http://127.0.0.1:8000.
3. Access the interactive documentation to explore endpoints:
   * Swagger UI: http://127.0.0.1:8000/docs
   * ReDoc: http://127.0.0.1:8000/redoc

## Using test_api.py to Test the API
* The test_api.py script allows you to test the /risk_assessments/{process_id} endpoints by sending POST and DELETE requests.

### Steps to Run test_api.py
1. Edit test_api.py (Optional): Update the file paths for the configuration and data files if they are in different directories.
    * Ensure the following files are available in the directory:
      * attribute_config.yaml 
      * risk_assessment_config.yaml 
      * original_data.csv 
      * synthetic_data.csv
      * (Optional) holdout_data.csv
2. Run the script:
```
python test_api.py
```
Expected Output:

The script will output the results of the API calls to the console. You should see responses for both starting and canceling a risk assessment.
Example Output
```
Start Risk Assessment Response: 202 {'status': '202', 'message': 'Risk assessment started', 'session_key': 123, 'pid': 45678}
Cancel Risk Assessment Response: 200 {'status': '200', 'message': 'Risk assessment canceled', 'session_key': 123, 'pid': 45678}
```

# Notes
Process ID: In test_api.py, the process_id variable can be adjusted to test with different IDs.
Running with Docker: If you prefer, you can create a Docker container to run the app. See FastAPI and Gunicorn documentation for details on using Docker.
Error Handling: If an error occurs, check that the Gunicorn server is running and that all required files are present.