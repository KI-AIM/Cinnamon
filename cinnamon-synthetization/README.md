# KI-AIM Synthetization Module

The use of artificial intelligence (AI) in medicine is still in its infancy and therefore offers great potential. For example, artificial intelligence can be used to provide physicians with information on the optimal treatment of patients in everyday treatment through decision support systems. Research and diagnosis of diseases can also be supported by AI methods, thereby facilitating the work of medical professionals. In order to develop AI methods such as decision support systems, however, large amounts of sensitive patient data are required. This data is often stored in hospital information systems and is subject to the General Data Protection Regulation (GDPR), as this data is particularly worthy of protection. This makes it difficult to provide medical data for research and development.

## Aim of the Project

In the project "AI-based anonymization in medicine" (KI-AIM), an anonymization platform is being developed that makes it possible to generate anonymous medical data in order to use it profitably in business and research. In this repository only the synthetization module is implemented. 

## Installation with Docker

Detailed installation instructions are found further below.
For simplicity, we also provide a `Dockerfile` that takes care of all installation steps.
After installing and starting [Docker](https://www.docker.com/get-started/), it suffices to run the following from command line (in the directory of our artifact):

1. Clone the repository to your local machine using the following command or by downloading the repository as a zip file and extracting it to a local directory:
```bash
git clone https://github.com/KI-AIM/Synthetization_Module.git
```
2. Build the docker container: `docker build -t synthetization_module .`
3. Run the container in interactive mode: `docker run -p 5000:5000 synthetization_module`
4. One can now run and test the APIs by using [Postman](https://www.postman.com). The APIs are available in our sharepoint folder Development/APIs. 


## API Documentation

### Get Requests
Get available synthesizers with their metadata and URL which leads to the seperate synthesizer forntend configurations.
A YAML is returned: 
```
http://127.0.0.1:5000/get_algorithms
```
Get the frontend configuration as YAML for a specific synthesizer in this case ctgan:
```
http://127.0.0.1:5000/synthetic_tabular_data_generator/synthesizer_config/ctgan.yaml
```
Get the status of the synthetization process as JSON (here JSON is chosen because of communication file), here the session_key is needed in the form-data: 
```
http://127.0.0.1:5000/get_status
```

### Post Requests
The Post request needs three file as input (form-data) consisting of the real data (csv), the attribute configuration (YAML) and 
the synthesizer configuration (YAML). Also, the session_key must be provided as form-data element as well as the callback API. The naming in the data-form must be the following: 
- data: The real data as csv file
- attribute_config: The attribute configuration as YAML file
- algorithm_config: The synthesizer configuration as YAML file
- session_key: The session key as string
- callback: The callback API as string

At the moment only CTGAN is tested, PAR will not work at the moment because the attribute config needs to be changed. 
The callback API will receive the following artifacts in the form-data:
- The synthetic data as csv file (synthetic_data)
- The training data as csv file (train)
- The testing data csv file (test)
- The model as pickle file (model)

The keys are written behind in brackets. 
```
http://127.0.0.1:5000/start_synthetization_process/ctgan
```

A running process can be canceled by providing the session_key and pid in the form-data and executing following API: 
- session_key: The session key as string
- pid: The process id as string, the process id is returned by the start_synthetization_process API
```
http://127.0.0.1:5000/cancel_synthetization_process
```