import re
import threading
import time
import os
import datetime
from typing import Any

import requests
import yaml
from requests.auth import HTTPBasicAuth

from sim_config import SimConfig, Profile

NUMBER_PROJECTS = 1
GAP = 0

class CinnamonContext:
    url: str
    email: str
    password: str

    config_file: str
    data_file: str | None
    result_directory: str

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


def delete_workflow(context: CinnamonContext) -> tuple[bool, str]:
    url = f"{context.url}/workflow/{context.workflow_id}"
    response = requests.delete(url, auth=create_auth(context))

    filename = f"{context.email}.zip"
    content_disposition = response.headers.get('Content-Disposition')
    if content_disposition:
        matches = re.findall(r'filename="?([^";\n]+)"?', content_disposition)
        if matches:
            filename = matches[0]

    result_path = f"{context.result_directory}/{filename}"
    with open(result_path, "wb") as handle:
        for data in response.iter_content():
            handle.write(data)

    return response.status_code == 200, result_path


def workflow(context: CinnamonContext) -> tuple[bool, str]:
    delete_user(context)
    register_user(context)

    status = post_start_workflow(context)
    if status is None:
        return False
    print_info(context, status)

    while status['pipeline']['currentStageIndex'] is not None:
        time.sleep(10)
        status = get_workflow_status(context)
        print_info(context, status)

    return delete_workflow(context)


def single_execution(profile: Profile):
    if profile.result_directory is None:
        print("Target directory not specified in profile.")
        return

    threads = []

    for project_index in range(NUMBER_PROJECTS):
        context = CinnamonContext()
        context.url = profile.cinnamon_url
        context.email = "project" + str(project_index)
        context.password = "Project" + str(project_index)
        context.config_file = profile.config_file
        context.data_file = profile.data_file
        context.result_directory = profile.result_directory

        thread = threading.Thread(target=workflow, args=(context,))
        threads.append(thread)

    for project_index in range(NUMBER_PROJECTS):
        if GAP > 0:
            time.sleep(GAP)
        threads[project_index].start()

    for project_index in range(NUMBER_PROJECTS):
        threads[project_index].join()


def directory_execution(profile: Profile):
    if profile.source_directory is None:
        print("Source directory not specified in profile.")
        return

    history_path = os.path.join(profile.source_directory, "history.yml")
    if os.path.exists(history_path):
        with open(history_path, "r") as f:
            history_data = yaml.safe_load(f) or {}
    else:
        history_data = {"history": []}

    if "history" not in history_data:
        history_data["history"] = []

    if "runs" not in history_data:
        history_data["runs"] = []
    history_data["runs"].append({"start_time": datetime.datetime.now().isoformat()})

    handled_files = {item["dataset"] for item in history_data["history"] if "dataset" in item}

    csv_files = [f for f in os.listdir(profile.source_directory) if f.endswith(".csv")]
    new_files = [f for f in csv_files if f not in handled_files]

    if not new_files:
        print("No new CSV files found.")
        with open(history_path, "w") as f:
            yaml.dump(history_data, f)
        return

    for filename in new_files:
        print(f"Processing {filename}...")
        context = CinnamonContext()
        context.url = profile.cinnamon_url
        context.email = "project0"
        context.password = "Project0"
        context.config_file = profile.config_file
        context.data_file = os.path.join(profile.source_directory, filename)
        context.result_directory = profile.result_directory if profile.result_directory is not None else profile.source_directory

        success, result_path = workflow(context)

        history_data["history"].append({
            "timestamp": datetime.datetime.now().isoformat(),
            "dataset": filename,
            "status": "success" if success else "failed",
            "result": result_path
        })

        # Save history after each file
        with open(history_path, "w") as f:
            yaml.dump(history_data, f)


def main():
    # Read the sim-config.yml
    config = SimConfig.from_yaml("sim-config.yml")

    active_profile = config.active_profile
    if active_profile is None:
        print("Active profile not found.")
        return

    print(f"Running with profile: {active_profile.name}")
    if active_profile.mode == 'single_execution':
        single_execution(active_profile)
    elif active_profile.mode == 'directory_execution':
        directory_execution(active_profile)
    else:
        print(f"Invalid mode: {active_profile.mode}")


if __name__ == "__main__":
    main()
