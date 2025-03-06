# KI-AIM Evaluation Module

The use of artificial intelligence (AI) in medicine is still in its infancy and therefore offers great potential. For example, artificial intelligence can be used to provide physicians with information on the optimal treatment of patients in everyday treatment through decision support systems. Research and diagnosis of diseases can also be supported by AI methods, thereby facilitating the work of medical professionals. In order to develop AI methods such as decision support systems, however, large amounts of sensitive patient data are required. This data is often stored in hospital information systems and is subject to the General Data Protection Regulation (GDPR), as this data is particularly worthy of protection. This makes it difficult to provide medical data for research and development.

## Aim of the Project

In the project "AI-based anonymization in medicine" (KI-AIM), an anonymization platform is being developed that makes it possible to generate anonymous medical data in order to use it profitably in business and research. In this repository only the evaluation module is implemented. 

## Installation with Docker

Detailed installation instructions are found further below.
For simplicity, we also provide a `Dockerfile` that takes care of all installation steps.
After installing and starting [Docker](https://www.docker.com/get-started/), it suffices to run the following from command line (in the directory of our artifact):

1. Clone the repository to your local machine using the following command or by downloading the repository as a zip file and extracting it to a local directory:
```bash
git clone https://github.com/KI-AIM/Evaluation_Module.git
```
2. Build the docker container: `docker build -t evaluation_module .`
3. Run the container in interactive mode: `docker run -p 5010:5010 evaluation_module`
4. One can now run and test the APIs by using [Postman](https://www.postman.com). The APIs are available in our sharepoint folder Development/APIs. 


## API Documentation

### Get Requests
Get available evaluation methods with their metadata which can be employed in the frontend.
A YAML is returned: 
```
http://127.0.0.1:5010/get_evaluation_metrics/cross-sectional
```
Here the evaluation method is cross-sectional, longitudinal and process-oriented. The returned YAML file can be used for the frontend.



### Post Requests
The Post request needs four files as input (real_data) consisting of the real data (csv), the (synthetic_data) 
consisting of the anonymized / synthesized data (csv) the attribute configuration (YAML) and 
the evaluation configuration (YAML). Also, the session_key must be provided as form-data element as well as the 
callback API. The naming in the data-form must be the following: 
- real_data: The real data as csv file
- synthetic_data: The synthetic data as csv file
- attribute_config: The attribute configuration as YAML file
- algorithm_config: The synthesizer configuration as YAML file
- session_key: The session key as string
- callback: The callback API as string

```
http://127.0.0.1:5010/start_evaluation
```

Also, there is POST request where the descriptive statistics of the real data can be calculated. The session_key and 
callback must be provided as form-data elements as well as the real data (csv) and the attribute_configuration (YAML). 
The naming in the data-form must be the following: 
- session_key: The session key as string
- real_data: The real data as csv file
- attribute_config: The attribute configuration as YAML file
- callback: The callback API as string

```
http://127.0.0.1:5010/calculate_descriptive_statistics
```




A running process can be canceled by providing the session_key and pid in the form-data and executing following API: 
- session_key: The session key as string
- pid: The process id as string, the process id is returned by the start_evaluation API
```
http://127.0.0.1:5010/cancel_synthetization_process
```