import re
import threading
import time
from typing import Any
import yaml

import requests
from requests.auth import HTTPBasicAuth

NUMBER_PROJECTS = 1
GAP = 0

class CinnamonContext:
    url: str
    email: str
    password: str

    config_file: str
    data_file: str

    workflow_id: str


def print_info(context: CinnamonContext, message: Any):
    print(f"[{context.email}] {message}")


def create_auth(context: CinnamonContext):
    return HTTPBasicAuth(context.email, context.password)


def register_user(context: CinnamonContext):
    url = f"{context.url}/user/register"
    form_data = {"email": context.email, "password": context.password, "passwordRepeated": context.password}
    response = requests.post(url, json=form_data)
    if response.status_code == 200:
        print_info(context, f"User created: {context.email}")
    else:
        print_info(context, f"Failed to create user: {response.json()}")


def login(context: CinnamonContext) -> bool:
    url = f"{context.url}/user/login"
    response = requests.get(url, auth=create_auth(context))
    if response.status_code == 200:
        print_info(context, f"Login successful: {context.email}")
        return True
    else:
        print_info(context, f"Failed to login: {response.json()}")
        return False


def delete_user(context: CinnamonContext):
    url = f"{context.url}/user/delete"
    files = {"email": (None, context.email), "password": (None, context.password)}
    response = requests.delete(url, auth=create_auth(context), files=files)
    if response.status_code == 200:
        print_info(context, f"User deleted: {context.email}")
    else:
        print_info(context, f"Failed to delete user: {response.json()}")


def post_start_workflow(context: CinnamonContext):
    url = f"{context.url}/workflow"

    if context.data_file is None:
        with open(context.config_file, "rb") as config_file:
            files = {"configuration": ("config.yaml", config_file, 'multipart/form-data')}
            response = requests.post(url, auth=create_auth(context), files=files)
    else:
        with open(context.data_file, "rb") as data_file:
            with open(context.config_file, "rb") as config_file:
                files = {"data": ('heart.csv', data_file, 'multipart/form-data'),
                         "configuration": ("config.yaml", config_file, 'multipart/form-data')}
                response = requests.post(url, auth=create_auth(context), files=files)

    if response.status_code != 202:
        print_info(context, response.json())
        return None
    else:
        response_body = response.json()
        context.workflow_id = response_body['workflowId']
        return response_body


def get_workflow_status(context: CinnamonContext):
    url = f"{context.url}/workflow/{context.workflow_id}"
    response = requests.get(url, auth=create_auth(context))
    return response.json()


def delete_workflow(context: CinnamonContext):
    url = f"{context.url}/workflow/{context.workflow_id}"
    response = requests.delete(url, auth=create_auth(context))

    filename = f"{context.email}.zip"
    content_disposition = response.headers.get('Content-Disposition')
    if content_disposition:
        matches = re.findall(r'filename="?([^";\n]+)"?', content_disposition)
        if matches:
            filename = matches[0]

    with open(f"./resources/{filename}", "wb") as handle:
        for data in response.iter_content():
            handle.write(data)

    return response.status_code == 200


def workflow(context: CinnamonContext):
    delete_user(context)
    register_user(context)

    status = post_start_workflow(context)
    if status is None:
        return
    print_info(context, status)

    while status['pipeline']['currentStageIndex'] is not None:
        time.sleep(10)
        status = get_workflow_status(context)
        print_info(context, status)

    delete_workflow(context)


def single_execution(profile):
    threads = []

    for project_index in range(NUMBER_PROJECTS):
        context = CinnamonContext()
        context.url = profile['cinnamon_url']
        context.email = "project" + str(project_index)
        context.password = "Project" + str(project_index)
        context.config_file = profile["config_file"]
        context.data_file = profile["data_file"]

        thread = threading.Thread(target=workflow, args=(context,))
        threads.append(thread)

    for project_index in range(NUMBER_PROJECTS):
        if GAP > 0:
            time.sleep(GAP)
        threads[project_index].start()

    for project_index in range(NUMBER_PROJECTS):
        threads[project_index].join()


def get_active_profile(config):
    active_profile = config.get("active_profile")
    for profile in config["profiles"]:
        if profile["name"] == active_profile:
            return profile
    return None


def main():
    # Read the sim-config.yml
    with open("sim-config.yml", 'r') as file:
        config = yaml.safe_load(file)

    active_profile = get_active_profile(config)
    if active_profile is None:
        print("Active profile not found.")

    print(f"Running with profile: {active_profile['name']}")
    if active_profile['mode'] == 'single_execution':
        single_execution(active_profile)
    else:
        print(f"Invalid mode: {active_profile['mode']}")


if __name__ == "__main__":
    main()
