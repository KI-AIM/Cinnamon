import re
import threading
import time
from typing import Any

import requests
from requests.auth import HTTPBasicAuth

# CINNAMON_URL = "https://cinnamon-demo.uni-muenster.de/api"
CINNAMON_URL = "http://localhost:8080/api"
DATA_FILE = "./resources/heart.csv"
CONFIG_FILE = "./resources/config.yaml"

NUMBER_PROJECTS = 1
GAP = 0

class CinnamonContext:
    email: str
    password: str


def print_info(context: CinnamonContext, message: Any):
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


def delete_user(context: CinnamonContext):
    url = f"{CINNAMON_URL}/user/delete"
    files = {"email": (None, context.email), "password": (None, context.password)}
    response = requests.delete(url, auth=create_auth(context), files=files)
    if response.status_code == 200:
        print_info(context, f"User deleted: {context.email}")


def get_results(context: CinnamonContext):
    url = f"{CINNAMON_URL}/project/zip"
    response = requests.get(url, auth=create_auth(context))

    filename = f"{context.email}.zip"
    content_disposition = response.headers.get('Content-Disposition')
    if content_disposition:
        matches = re.findall(r'filename="?([^";\n]+)"?', content_disposition)
        if matches:
            filename = matches[0]

    with open(f"./resources/{filename}", "wb") as handle:
        for data in response.iter_content():
            handle.write(data)


def post_start_workflow(context: CinnamonContext):
    url = f"{CINNAMON_URL}/workflow"

    with open(DATA_FILE, "rb") as data_file:
        with open(CONFIG_FILE, "rb") as config_file:
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


def workflow(context: CinnamonContext):
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


def main():
    threads = []

    for project_index in range(NUMBER_PROJECTS):
        context = CinnamonContext()
        context.email = "project" + str(project_index)
        context.password = "Project" + str(project_index)

        thread = threading.Thread(target=workflow, args=(context,))
        threads.append(thread)

    for project_index in range(NUMBER_PROJECTS):
        if GAP > 0:
            time.sleep(GAP)
        threads[project_index].start()

    for project_index in range(NUMBER_PROJECTS):
        threads[project_index].join()

if __name__ == "__main__":
    main()
