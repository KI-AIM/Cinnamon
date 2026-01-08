import argparse
import io
import json
import re
import shutil
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple, List

import requests
from requests.auth import HTTPBasicAuth

CINNAMON_URL = "http://localhost:8080/api"
DEFAULT_DATASET = "cardiovascular"
DEFAULT_HOLDOUT = 0.3 # Nur für Stroke und cardiovascular auf 0.3 alle anderen 0.2
POLL_INTERVAL_SECONDS = 5
RUNS = 4
DEFAULT_SYNTH_ALGO = "arf"

# Run: python simulation/sim_anon.py

_TOP_LEVEL_KEY_RE = re.compile(r"^[A-Za-z0-9_-]+:\s*(#.*)?$")


@dataclass
class CinnamonContext:
    email: str
    password: str


def print_info(context: CinnamonContext, message: str):
    print(f"[{context.email}] {message}")


def create_auth(context: CinnamonContext):
    return HTTPBasicAuth(context.email, context.password)


def request_or_raise(response: requests.Response, action: str):
    if response.ok:
        return
    details = response.text
    raise RuntimeError(f"{action} failed: {response.status_code} {details}")


def register_user(context: CinnamonContext) -> bool:
    url = f"{CINNAMON_URL}/user/register"
    form_data = {
        "email": context.email,
        "password": context.password,
        "passwordRepeated": context.password,
    }
    response = requests.post(url, json=form_data)
    return response.status_code == 200


def login(context: CinnamonContext) -> bool:
    url = f"{CINNAMON_URL}/user/login"
    response = requests.get(url, auth=create_auth(context))
    return response.status_code == 200


def create_project(context: CinnamonContext):
    url = f"{CINNAMON_URL}/project"
    headers = {"Accept": "application/json"}
    print_info(context, "Creating project...")
    response = requests.post(
        url, files={"mode": (None, "STANDARD")}, auth=create_auth(context), headers=headers
    )
    request_or_raise(response, "create project")
    print_info(context, "Project created.")
    return response.json()


def delete_user(context: CinnamonContext):
    url = f"{CINNAMON_URL}/user/delete"
    files = {"email": (None, context.email), "password": (None, context.password)}
    response = requests.delete(url, auth=create_auth(context), files=files)
    if response.status_code == 200:
        print_info(context, f"User deleted: {context.email}")


def post_data(context: CinnamonContext, data_path: Path):
    url = f"{CINNAMON_URL}/data/file"
    print_info(context, f"Uploading data file: {data_path.name}")

    file_config = {"fileType": "CSV", "csvFileConfiguration": {}}
    file_config_payload = json.dumps(file_config)

    with data_path.open("rb") as file:
        files = {
            "file": (data_path.name, file, "multipart/form-data"),
            "fileConfiguration": (None, file_config_payload, "application/json"),
        }
        response = requests.post(url, auth=create_auth(context), files=files)
    request_or_raise(response, "upload data file")
    print_info(context, "Data file uploaded.")


def post_data_config(context: CinnamonContext, config_payload: str):
    url = f"{CINNAMON_URL}/data"
    print_info(context, "Uploading data configuration...")
    files = {"configuration": (None, config_payload, "application/x-yaml")}
    response = requests.post(url, auth=create_auth(context), files=files)
    request_or_raise(response, "upload data configuration")
    print_info(context, "Data configuration uploaded.")


def post_hold_out(context: CinnamonContext, hold_out_percentage: float):
    url = f"{CINNAMON_URL}/data/hold-out"
    print_info(context, f"Creating hold-out split ({hold_out_percentage:.2f})...")
    response = requests.post(
        url, auth=create_auth(context), data={"holdOutPercentage": hold_out_percentage}
    )
    request_or_raise(response, "create hold-out")
    print_info(context, "Hold-out split created.")


def post_confirm_data(context: CinnamonContext):
    url = f"{CINNAMON_URL}/data/confirm"
    print_info(context, "Confirming data...")
    response = requests.post(url, auth=create_auth(context))
    request_or_raise(response, "confirm data")
    print_info(context, "Data confirmed.")


def post_config(context: CinnamonContext, config_name: str, config_payload: str, algorithm_url: str):
    url = f"{CINNAMON_URL}/config"
    print_info(context, f"Uploading {config_name} configuration...")
    files = {
        "configurationName": (None, config_name),
        "configuration": (None, config_payload, "application/x-yaml"),
        "url": (None, algorithm_url),
    }
    response = requests.post(url, auth=create_auth(context), files=files)
    request_or_raise(response, f"upload {config_name} configuration")
    print_info(context, f"{config_name} configuration uploaded.")


def post_configure(context: CinnamonContext, job_name: str, skip: bool = False):
    url = f"{CINNAMON_URL}/process/configure"
    if skip:
        print_info(context, f"Configuring job: {job_name} (skip)")
    else:
        print_info(context, f"Configuring job: {job_name}")
    files = {"jobName": (None, job_name)}
    if skip:
        files["skip"] = (None, "true")
    response = requests.post(url, auth=create_auth(context), files=files)
    request_or_raise(response, f"configure job {job_name}")
    if skip:
        print_info(context, f"Job configured: {job_name} (skip)")
    else:
        print_info(context, f"Job configured: {job_name}")


def post_run_stage(context: CinnamonContext, stage_name: str, job_name: Optional[str] = None):
    if job_name:
        url = f"{CINNAMON_URL}/process/{stage_name}/start/{job_name}"
    else:
        url = f"{CINNAMON_URL}/process/{stage_name}/start"
    print_info(
        context,
        f"Starting stage: {stage_name}" if not job_name else f"Starting stage: {stage_name} ({job_name})",
    )
    response = requests.post(url, auth=create_auth(context))
    request_or_raise(response, f"start stage {stage_name}")
    return response.json()


def get_process_status(context: CinnamonContext, stage_name: str):
    url = f"{CINNAMON_URL}/process"
    response = requests.get(url, auth=create_auth(context))
    request_or_raise(response, "get process status")
    response_json = response.json()
    for stage in response_json.get("stages", []):
        if stage.get("stageName") == stage_name:
            return stage
    return None


def run_stage(context: CinnamonContext, stage_name: str, job_name: Optional[str] = None):
    stage_start = time.time()
    post_run_stage(context, stage_name, job_name)
    status = get_process_status(context, stage_name)
    while status and status.get("status") in ("RUNNING", "SCHEDULED"):
        time.sleep(POLL_INTERVAL_SECONDS)
        status = get_process_status(context, stage_name)

    if not status:
        raise RuntimeError(f"Stage {stage_name} not found in process status.")
    if status.get("status") not in ("FINISHED",):
        raise RuntimeError(f"Stage {stage_name} did not finish successfully: {status}")

    stage_end = time.time()
    label = stage_name if not job_name else f"{stage_name} ({job_name})"
    print_info(context, f"Stage {label} took {stage_end - stage_start:.2f} seconds")


def get_results_zip(context: CinnamonContext, resources: List[str], label: str):
    url = f"{CINNAMON_URL}/project/zip"
    print_info(context, f"Downloading {label} results...")
    params = {"resources": resources}
    response = requests.get(url, auth=create_auth(context), params=params)
    request_or_raise(response, f"download {label} results")
    print_info(context, f"{label} results downloaded.")
    return response.content


def safe_extract_member(zip_file: zipfile.ZipFile, member: str, output_path: Path):
    resolved_output_dir = output_path.parent.resolve()
    resolved_target = output_path.resolve()
    if not str(resolved_target).startswith(str(resolved_output_dir)):
        raise RuntimeError(f"Unsafe path in zip: {member}")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with zip_file.open(member) as source, output_path.open("wb") as target:
        shutil.copyfileobj(source, target)


def save_anonymized_datasets(zip_payload: bytes, output_dir: Path, timestamp: str):
    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"[sim_anon] Saving results to {output_dir} (timestamp {timestamp})")
    with zipfile.ZipFile(io.BytesIO(zip_payload)) as zip_file:
        members = [name for name in zip_file.namelist() if name.endswith(".csv")]
        if not members:
            members = [name for name in zip_file.namelist() if not name.endswith("/")]
        if not members:
            raise RuntimeError("No result files found in the zip response.")
        for member in members:
            member_path = Path(member)
            if member_path.suffix:
                output_name = f"{member_path.stem}_{timestamp}{member_path.suffix}"
            else:
                output_name = f"{member_path.name}_{timestamp}"
            safe_extract_member(zip_file, member, output_dir / output_name)


def save_result_files(zip_payload: bytes, output_dir: Path, timestamp: str, label: str):
    output_dir.mkdir(parents=True, exist_ok=True)
    print(f"[sim_anon] Saving {label} results to {output_dir} (timestamp {timestamp})")
    with zipfile.ZipFile(io.BytesIO(zip_payload)) as zip_file:
        members = [name for name in zip_file.namelist() if not name.endswith("/")]
        if not members:
            raise RuntimeError("No result files found in the zip response.")
        for member in members:
            member_path = Path(member)
            if member_path.suffix:
                output_name = f"{member_path.stem}_{timestamp}{member_path.suffix}"
            else:
                output_name = f"{member_path.name}_{timestamp}"
            safe_extract_member(zip_file, member, output_dir / output_name)


def extract_top_level_section(text: str, key: str) -> Optional[str]:
    lines = text.splitlines(keepends=True)
    key_re = re.compile(rf"^{re.escape(key)}:\s*(#.*)?$")
    start_index = None
    for index, line in enumerate(lines):
        if key_re.match(line):
            start_index = index
            break
    if start_index is None:
        return None
    block = [lines[start_index]]
    for line in lines[start_index + 1 :]:
        if _TOP_LEVEL_KEY_RE.match(line):
            break
        block.append(line)
    if block and not block[-1].endswith("\n"):
        block[-1] += "\n"
    return "".join(block)


def extract_synthesizer_name(text: str) -> str:
    for line in text.splitlines():
        match = re.match(r"^\s*synthesizer:\s*([A-Za-z0-9_-]+)\s*$", line)
        if match:
            return match.group(1)
    return DEFAULT_SYNTH_ALGO


def resolve_dataset_paths(
    base_dir: Path, dataset: str, config_path: Optional[str], data_path: Optional[str]
) -> Tuple[Path, Path]:
    dataset_dir = base_dir / "dataset_configs" / dataset
    if config_path:
        config_file = Path(config_path)
        if not config_file.is_absolute():
            config_file = base_dir / config_file
    else:
        config_file = dataset_dir / "all-configurations.yaml"
    if not config_file.exists():
        raise FileNotFoundError(f"Configuration file not found: {config_file}")

    if data_path:
        data_file = Path(data_path)
        if not data_file.is_absolute():
            data_file = base_dir / data_file
    else:
        data_file = dataset_dir / "original-dataset.csv"
        if not data_file.exists():
            csv_files = sorted(dataset_dir.glob("*.csv"))
            if not csv_files:
                raise FileNotFoundError(f"No CSV files found in {dataset_dir}")
            data_file = csv_files[0]
    if not data_file.exists():
        raise FileNotFoundError(f"Dataset file not found: {data_file}")

    return config_file, data_file


def main():
    global CINNAMON_URL
    parser = argparse.ArgumentParser(description="Run anonymization via Cinnamon API.")
    parser.add_argument("--dataset", default=DEFAULT_DATASET, help="Dataset folder name.")
    parser.add_argument("--config", help="Path to all-configurations.yaml.")
    parser.add_argument("--data", help="Path to the input CSV dataset.")
    parser.add_argument("--output-dir", help="Directory to write anonymized datasets.")
    parser.add_argument("--cinnamon-url", default=CINNAMON_URL, help="Cinnamon API base URL.")
    parser.add_argument("--email", help="User email for the Cinnamon API.")
    parser.add_argument("--password", help="Password for the Cinnamon API.")
    parser.add_argument("--holdout", type=float, default=DEFAULT_HOLDOUT, help="Hold-out percentage.")
    parser.add_argument("--keep-user", action="store_true", help="Do not delete the user after run.")
    parser.add_argument("--runs", type=int, default=RUNS, help="Number of runs to execute.")
    args = parser.parse_args()

    base_url = args.cinnamon_url.rstrip("/")
    if not base_url.endswith("/api"):
        base_url = f"{base_url}/api"
    CINNAMON_URL = base_url

    base_dir = Path(__file__).resolve().parent
    config_file, data_file = resolve_dataset_paths(
        base_dir, args.dataset, args.config, args.data
    )

    output_dir = (
        Path(args.output_dir)
        if args.output_dir
        else base_dir / "experiment_results" / args.dataset / "anon" / "files"
    )
    if not output_dir.is_absolute():
        output_dir = base_dir / output_dir

    config_text = config_file.read_text(encoding="utf-8")
    data_config = extract_top_level_section(config_text, "configurations")
    anonymization_config = extract_top_level_section(config_text, "anonymization")
    synthetization_config = extract_top_level_section(config_text, "synthetization_configuration")
    evaluation_config = extract_top_level_section(config_text, "evaluation_configuration")
    risk_config = extract_top_level_section(config_text, "risk_assessment_configuration")
    if not data_config:
        raise RuntimeError(f"No 'configurations' section found in {config_file}")
    if not anonymization_config:
        raise RuntimeError(f"No 'anonymization' section found in {config_file}")
    if not synthetization_config:
        raise RuntimeError(f"No 'synthetization_configuration' section found in {config_file}")
    if not evaluation_config:
        raise RuntimeError(f"No 'evaluation_configuration' section found in {config_file}")
    if not risk_config:
        raise RuntimeError(f"No 'risk_assessment_configuration' section found in {config_file}")

    synth_algo = extract_synthesizer_name(synthetization_config)
    synth_algo_url = f"/start_synthetization_process/{synth_algo}"

    if args.runs < 1:
        raise ValueError("--runs must be at least 1.")

    for run_index in range(args.runs):
        run_suffix = f"run{run_index + 1:02d}"
        run_timestamp = f"{time.strftime('%Y%m%d_%H%M%S')}_{run_suffix}"
        email = args.email or f"sim_anon_{int(time.time() * 1000)}_{run_suffix}"
        password = args.password or "Project0!Test"
        context = CinnamonContext(email=email, password=password)

        print_info(context, f"Starting run {run_index + 1}/{args.runs}")
        if not login(context):
            if not register_user(context):
                raise RuntimeError("Registration failed. Provide --email/--password for an existing user.")
            if not login(context):
                raise RuntimeError("Login failed after registration.")

        try:
            create_project(context)
            post_data(context, data_file)
            post_data_config(context, data_config)
            if args.holdout and args.holdout > 0:
                post_hold_out(context, args.holdout)
            post_confirm_data(context)

            post_config(context, "anonymization", anonymization_config, "/api/anonymization/")
            post_config(
                context, "synthetization_configuration", synthetization_config, synth_algo_url
            )
            post_config(context, "evaluation_configuration", evaluation_config, "/start_evaluation")
            post_config(context, "risk_assessment_configuration", risk_config, "/start_evaluation")

            print_info(context, "Phase 0: real risk evaluation")
            post_configure(context, "technical_evaluation", skip=True)
            post_configure(context, "risk_evaluation")
            run_stage(context, "evaluation")

            real_risk_dir = (
                base_dir / "experiment_results" / args.dataset / "real_risk"
            )
            risk_zip = get_results_zip(
                context, ["pipeline.evaluation.risk_evaluation.other"], "risk evaluation (real)"
            )
            save_result_files(risk_zip, real_risk_dir, run_timestamp, "risk evaluation (real)")

            print_info(context, "Phase 1: anonymization + evaluation")
            post_configure(context, "anonymization")
            post_configure(context, "synthetization", skip=True)
            post_configure(context, "technical_evaluation", skip=False)
            post_configure(context, "risk_evaluation")

            run_stage(context, "execution")
            run_stage(context, "evaluation")

            anon_zip = get_results_zip(
                context, ["pipeline.execution.anonymization.dataset"], "anonymization"
            )
            save_anonymized_datasets(anon_zip, output_dir, run_timestamp)

            anon_tech_eval_dir = (
                base_dir / "experiment_results" / args.dataset / "anon" / "tech_eval"
            )
            anon_risk_eval_dir = (
                base_dir / "experiment_results" / args.dataset / "anon" / "risk_eval"
            )

            tech_zip = get_results_zip(
                context,
                ["pipeline.evaluation.technical_evaluation.other"],
                "technical evaluation",
            )
            save_result_files(tech_zip, anon_tech_eval_dir, run_timestamp, "technical evaluation")

            risk_zip = get_results_zip(
                context, ["pipeline.evaluation.risk_evaluation.other"], "risk evaluation"
            )
            save_result_files(risk_zip, anon_risk_eval_dir, run_timestamp, "risk evaluation")
            print_info(context, f"Saved anonymized datasets to {output_dir}")

            print_info(context, "Phase 2: synthetization + evaluation")
            post_configure(context, "synthetization", skip=False)
            run_stage(context, "execution", job_name="synthetization")
            run_stage(context, "evaluation")

            anon_synth_zip = get_results_zip(
                context, ["pipeline.execution.synthetization.dataset"], "synthetization"
            )
            anon_synth_output_dir = (
                base_dir / "experiment_results" / args.dataset / "anon_synth" / "files"
            )
            save_anonymized_datasets(anon_synth_zip, anon_synth_output_dir, run_timestamp)

            anon_synth_tech_eval_dir = (
                base_dir / "experiment_results" / args.dataset / "anon_synth" / "tech_eval"
            )
            tech_zip = get_results_zip(
                context,
                ["pipeline.evaluation.technical_evaluation.other"],
                "technical evaluation",
            )
            save_result_files(
                tech_zip, anon_synth_tech_eval_dir, run_timestamp, "technical evaluation"
            )

            anon_synth_risk_eval_dir = (
                base_dir / "experiment_results" / args.dataset / "anon_synth" / "risk_eval"
            )
            risk_zip = get_results_zip(
                context, ["pipeline.evaluation.risk_evaluation.other"], "risk evaluation"
            )
            save_result_files(
                risk_zip, anon_synth_risk_eval_dir, run_timestamp, "risk evaluation"
            )
        finally:
            if not args.keep_user:
                delete_user(context)


if __name__ == "__main__":
    main()
