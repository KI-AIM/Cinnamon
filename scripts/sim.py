import io
import json
import requests
import time
import threading
from requests.auth import HTTPBasicAuth

# CINNAMON_URL = "https://cinnamon-demo.uni-muenster.de/api"
CINNAMON_URL = "http://localhost:8080/api"
RESOURCE_DIR = "../resources/UsabilityEvaluation/Scenario"
NUMBER_PROJECTS = 1

GAP = 0

# Configurations
ATTRIBUTE_CONFIG = "/solution/original-attribute_config.yaml"
ANON_CONFIG = "/solution/anonymization.yaml"
SYNTH_CONFIG = "/solution/synthetization_configuration.yaml"
TECHNICAL_EVAL_CONFIG = "/solution/evaluation_configuration.yaml"
RISK_CONFIG = "/solution/risk_assessment_configuration.yaml"


class CinnamonContext:
    email: str
    password: str


def print_info(context: CinnamonContext, message: str):
    print(f"[{context.email}] {message}")


def create_auth(context: CinnamonContext):
    return HTTPBasicAuth(context.email, context.password)


def register_user(context: CinnamonContext):
    url = f"{CINNAMON_URL}/user/register"
    form_data = {"email": context.email, "password": context.password, "passwordRepeated": context.password}
    response = requests.post(url, json=form_data)
    return response.status_code == 200


def login(context: CinnamonContext) -> bool:
    url = f"{CINNAMON_URL}/user/login"
    response = requests.get(url, auth=create_auth(context))
    print_info(context, response.status_code)
    return response.status_code == 200


def create_project(context: CinnamonContext):
    url = f"{CINNAMON_URL}/project"
    headers = {'Accept': 'application/json'}
    response = requests.post(url, files={"mode":  ("abc", "abc")}, auth=create_auth(context), headers=headers)
    if response.status_code == 200:
        print_info(context, "Created new project")
        return response.json()
    else:
        print_info(context, f"Failed to create new project: {response.status_code}")
        return None


def delete_user(context: CinnamonContext):
    url = f"{CINNAMON_URL}/user/delete"
    files = {"email": (None, context.email), "password": (None, context.password)}
    response = requests.delete(url, auth=create_auth(context), files=files)
    if response.status_code == 200:
        print_info(context, f"User deleted: {context.email}")


def post_data(context: CinnamonContext):
    url = f"{CINNAMON_URL}/data/file"

    file_config = {"fileType": "CSV", "csvFileConfiguration": {}}
    file_config = json.dumps(file_config)
    file_config = file_config.encode('utf-8')
    file_config = io.BytesIO(file_config)

    with open(f"{RESOURCE_DIR}/heart.csv", "rb") as file:
        files = {"file": ('heart.csv', file, 'multipart/form-data'), "fileConfiguration": (None, file_config, 'application/json')}
        response = requests.post(url, auth=create_auth(context), files=files)
        print_info(context, response.json())


def post_data_config(context: CinnamonContext):
    url = f"{CINNAMON_URL}/data"

    with open(f"{RESOURCE_DIR}{ATTRIBUTE_CONFIG}", "r") as file:
        files = {"configuration": (None, file, 'application/x-yaml')}
        response = requests.post(url, auth=create_auth(context), files=files)
        print_info(context, response.json())


def post_hold_out(context: CinnamonContext):
    data = {"holdOutPercentage": 0.2}
    url = f"{CINNAMON_URL}/data/hold-out"
    response = requests.post(url, auth=create_auth(context), data=data)
    print_info(context, f"Holdout data: {response.status_code}")


def post_confirm_data(context: CinnamonContext):
    url = f"{CINNAMON_URL}/data/confirm"
    response = requests.post(url, auth=create_auth(context))
    print_info(context, f"Data confirmed: {response.status_code}")


def post_config(context: CinnamonContext, config_name: str, config_path: str, algorithm_url: str):
    url = f"{CINNAMON_URL}/config"

    with open(f"{RESOURCE_DIR}{config_path}", "r") as file:
        files = {"configurationName": (None, config_name), "configuration": (None, file), "url": (None, algorithm_url)}
        response = requests.post(url, auth=create_auth(context), files=files)
        print_info(context, f"Status {config_name} code: {response.status_code}")


def post_configure(context: CinnamonContext, job_name: str):
    url = f"{CINNAMON_URL}/process/configure"
    files = {"jobName": (None, job_name)}
    response = requests.post(url, auth=create_auth(context), files=files)
    if response.status_code != 200:
        print_info(context, response.json())
    else:
        print_info(context, f"Configured job: {job_name}")


def post_run_stage(context: CinnamonContext, stage_name: str):
    url = f"{CINNAMON_URL}/process/{stage_name}/start"
    response = requests.post(url, auth=create_auth(context))
    print_info(context, f"Status {stage_name} code: {response.status_code}, response: {response.json()}")


def get_process_status(context: CinnamonContext, stage_name: str):
    url = f"{CINNAMON_URL}/process"
    response = requests.get(url, auth=create_auth(context))
    response_json = response.json()
    stages = response_json["stages"]
    for stage in stages:
        if stage["stageName"] == stage_name:
            return stage
    return None


def run_stage(context: CinnamonContext, stage_name: str):
    stage_start = time.time()

    post_run_stage(context, stage_name)
    status = get_process_status(context, stage_name)
    print_info(context, status)
    while status["status"] == "RUNNING" or status["status"] == "SCHEDULED":
        time.sleep(5)
        status = get_process_status(context, stage_name)
        print_info(context, status)

    stage_end = time.time()
    print_info(context, f"Stage {stage_name} took {stage_end - stage_start} seconds")


def get_results(context: CinnamonContext):
    url = f"{CINNAMON_URL}/project/zip"
    response = requests.get(url, auth=create_auth(context))
    with open(context.email +  ".zip", "wb") as handle:
        for data in response.iter_content():
            handle.write(data)


def post_start_workflow(context: CinnamonContext):
    url = f"{CINNAMON_URL}/workflow"

    with open(f"./heart.csv", "rb") as data_file:
        with open(f"./config.yaml", "rb") as config_file:
            files = {"data": ('heart.csv', data_file, 'multipart/form-data'),
                     "configuration": ("config.yaml", config_file, 'multipart/form-data')}
            response = requests.post(url, auth=create_auth(context), files=files)

    if response.status_code != 200:
        print_info(context, response.json())
        return None
    else:
        return response.json()


def get_workflow_status(context: CinnamonContext):
    url = f"{CINNAMON_URL}/workflow"
    response = requests.get(url, auth=create_auth(context))
    return response.json()


def workflow_new(context: CinnamonContext):
    if not login(context):
        register_user(context)
        login(context)

    status = get_workflow_status(context)
    print_info(context, status)

    status = post_start_workflow(context)
    if status is None:
        return
    print_info(context, status)

    while status['currentStageIndex'] is not None:
        time.sleep(10)
        status = get_workflow_status(context)
        print_info(context, status)

    get_results(context)


def workflow(context: CinnamonContext):
    start_time = time.time()

    # Clean
    delete_user(context)

    if not login(context):
        register_user(context)
        login(context)

    project = create_project(context)
    print_info(context, project)

    # Upload data
    post_data(context)
    post_data_config(context)
    post_hold_out(context)
    post_confirm_data(context)

    # Upload configurations
    post_config(context, "anonymization", ANON_CONFIG, "/api/anonymization/")
    post_config(context, "synthetization_configuration", SYNTH_CONFIG, "/start_synthetization_process/arf")
    post_config(context, "evaluation_configuration", TECHNICAL_EVAL_CONFIG, "/start_evaluation")
    post_config(context, "risk_assessment_configuration", RISK_CONFIG, "/start_evaluation")

    # configure jobs
    post_configure(context, "anonymization")
    post_configure(context, "synthetization")
    post_configure(context, "technical_evaluation")
    post_configure(context, "risk_evaluation")

    # run
    run_stage(context, "execution")
    run_stage(context, "evaluation")

    # export result
    get_results(context)

    # Clean
    delete_user(context)

    end_time = time.time()
    print_info(context, f"Total time: {end_time - start_time} seconds")


def main():
    threads = []

    for project_index in range(NUMBER_PROJECTS):
        context = CinnamonContext()
        context.email = "project" + str(project_index)
        context.password = "Project" + str(project_index)

        thread = threading.Thread(target=workflow_new, args=(context,))
        threads.append(thread)

    for project_index in range(NUMBER_PROJECTS):
        if GAP > 0:
            time.sleep(GAP)
        threads[project_index].start()

    for project_index in range(NUMBER_PROJECTS):
        threads[project_index].join()

if __name__ == "__main__":
    main()
