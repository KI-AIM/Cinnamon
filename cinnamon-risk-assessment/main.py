import errno
import json
import multiprocessing
import os
import io
from typing import Optional

import pandas as pd
import yaml
from fastapi import FastAPI, Form, File, UploadFile, HTTPException, Path, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from base_assessment.general_assessment_process import general_assessment
from models.AttributeConfig import AttributeConfigList
from models.RiskAssessmentConfig import RiskAssessmentConfig
from risk_assessment.RiskAssessmentProcess import risk_assessment

############## SECTION APP DEFINITION ###################
app = FastAPI()

origins = [
    "http://localhost:8000"
    "http://127.0.0.1:8000"
]

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
##################### END SECTION ###################

tasks = {}
task_locks = {}


def kill_process(process_id):
    if process_id not in tasks:
        return {"status": "200", 'message': 'Risk assessment canceled', 'session_key': process_id}

    try:
        task_process = tasks[process_id]
        task_pid = task_process.pid
        os.kill(int(task_pid), 9)

        return {"status": "200", 'message': 'Risk assessment canceled', 'session_key': process_id, 'pid': task_pid}
    except OSError as err:
        if err.errno == errno.ESRCH:
            # ESRCH == No such process
            return {"status": "200", 'message': 'Risk assessment canceled', 'session_key': process_id, 'pid': task_pid}
        elif err.errno == errno.EPERM:
            # EPERM clearly means there's a process to deny access to
            raise HTTPException(status_code=500, detail=f'Could not cancel process {process_id}')
        else:
            raise
    except Exception as e:
        # log e
        print("Exception:")
        print(e)
        raise HTTPException(status_code=500, detail=f'Exception occurred during cancellation of {process_id}')


@app.delete("/risk_assessments/{process_id}")
async def cancel_risk_assessment(
        process_id: str = Path()
):
    return kill_process(process_id)


def load_yaml_config(config_file: str):
    """Loads YAML configuration and returns it as a dictionary."""
    with open(config_file, "r") as file:
        return file.read()


@app.get("/algorithms")
async def get_algorithms():
    """GET endpoint to retrieve the available algorithms as YAML."""
    config = load_yaml_config(r"frontend_configs/algorithms.yaml")
    return Response(content=config, media_type='text/yaml')

@app.get("/risk_assessment_config")
async def get_risk_assessment_frontend_config():
    """GET endpoint to retrieve configuration as YAML."""
    config = load_yaml_config(r"frontend_configs/risk_assessment_config.yaml")
    return Response(content=config, media_type='text/yaml')


@app.post("/risk_assessments/{process_id}")
async def start_risk_assessment(
        process_id: str = Path(),
        callback_url: str = Form(...),
        attribute_config: UploadFile = File(...),
        risk_assessment_config: UploadFile = File(...),
        original_data: UploadFile = File(...),
        synthetic_data: UploadFile = File(...),
        holdout_data: Optional[UploadFile] = File(None),
):
    # TODO: check if actual rights exist to create task??
    attribute_config_model = await load_attribute_config(attribute_config)
    risk_assessment_config_model = await load_risk_assessment_config(risk_assessment_config)

    original_data_df = await load_dataset(original_data, "original")
    synthetic_data_df = await load_dataset(synthetic_data, "synthetic")

    holdout_data_df = None
    if holdout_data:
        holdout_data_df = await load_dataset(holdout_data, "hold out")

    print('Data successfully loaded')

    try:
        # Create and start the multiprocessing process, targeting the `risk_assessment` method of `process_instance`
        task_process = multiprocessing.Process(target=risk_assessment, args=(process_id, callback_url,
                                                                             attribute_config_model,
                                                                             risk_assessment_config_model,
                                                                             original_data_df,
                                                                             synthetic_data_df,
                                                                             holdout_data_df))
        tasks[process_id] = task_process
        task_process.start()
        pid = task_process.pid

        return {"status": "202", 'message': 'Risk assessment started', 'session_key': process_id, 'pid': pid}

    except Exception as e:
        # log e
        raise HTTPException(status_code=500,
                            detail=f'Exception occurred during risk assessment for process {process_id}')


@app.post("/base_assessments/{process_id}")
async def start_base_assessment(
        process_id: str = Path(),
        callback_url: str = Form(...),
        attribute_config: UploadFile = File(...),
        risk_assessment_config: UploadFile = File(...),
        original_data: UploadFile = File(...)
):
    # TODO: check if actual rights exist to create task??
    attribute_config_model = await load_attribute_config(attribute_config)
    risk_assessment_config_model = await load_risk_assessment_config(risk_assessment_config)

    original_data_df = await load_dataset(original_data, "original")

    print('Data successfully loaded')

    try:
        # Create and start the multiprocessing process, targeting the `risk_assessment` method of `process_instance`
        task_process = multiprocessing.Process(target=general_assessment, args=(process_id, callback_url,
                                                                                attribute_config_model,
                                                                                risk_assessment_config_model,
                                                                                original_data_df))
        tasks[process_id] = task_process
        task_process.start()
        pid = task_process.pid

        return {"status": "202", 'message': 'Risk assessment started', 'session_key': process_id, 'pid': pid}

    except Exception as e:
        # log e
        raise HTTPException(status_code=500,
                            detail=f'Exception occurred during risk assessment for process {process_id}')


async def load_dataset(data, dataset_name):
    try:
        data_content = await data.read()
        data_df = pd.read_csv(io.StringIO(data_content.decode('utf-8')))
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Error when importing {dataset_name} data: {e}")
    return data_df


async def load_risk_assessment_config(risk_assessment_config):
    try:
        risk_assessment_config_data = yaml.safe_load(await risk_assessment_config.read())
        risk_assessment_config_model = RiskAssessmentConfig(
            **risk_assessment_config_data["risk_assessment_configuration"])
    except yaml.YAMLError as yaml_error:
        raise HTTPException(status_code=400, detail=f"Invalid YAML in risk_assessment_config: {yaml_error}")
    except ValidationError as validation_error:
        raise HTTPException(status_code=400, detail=f"Attribute config validation error: {validation_error}")
    return risk_assessment_config_model


async def load_attribute_config(attribute_config):
    try:
        attribute_config_data = yaml.safe_load(await attribute_config.read())
        attribute_config_model = AttributeConfigList(**attribute_config_data)
    except yaml.YAMLError as yaml_error:
        raise HTTPException(status_code=400, detail=f"Invalid YAML in attribute_config: {yaml_error}")
    except ValidationError as validation_error:
        raise HTTPException(status_code=400, detail=f"Attribute config validation error: {validation_error}")
    return attribute_config_model


@app.delete("/base_assessments/{process_id}")
async def cancel_base_assessment(process_id: str = Path()):
    return kill_process(process_id)


@app.post("/report")
async def get_report(
        risks: UploadFile = File(...),
):
    risks_data = json.load(risks.file)

    low_reasons = []
    high_reasons = []

    linkage = risks_data['linkage_health_risk']['risk_value']
    if linkage < 0.5:
        low_reasons.append('Overall low risk that the protected dataset can be used for data linkage')
    else:
        high_reasons.append('Overall high risk that the protected dataset can be used for data linkage')

    inference = risks_data['inference_average_risk']['priv_risk']
    if inference < 0.5:
        low_reasons.append('Overall low risk that the protected dataset can be used for attribute inference attacks')
    else:
        high_reasons.append('Overall high risk that the protected dataset can be used for attribute inference attacks')

    singling_out = risks_data['univariate_singling_out_risk']['risk_value']
    if singling_out < 0.5:
        low_reasons.append('Overall low risk that records in the protected data could be clearly separated from other records')
    else:
        high_reasons.append('Overall high risk that records in the protected data could be clearly separated from other records')

    overview = '<div class="report-text-block">Scores approaching 1.0 indicate high privacy, while scores near 0.0 signify low privacy and therefore high potential reidentification risks. The probability of the likelihood of a risk is inverse to the privacy score.</div>'
    overview += f'<div class="report-split"><div class="report-metric-card"><div><strong>Identified High Privacy Risk</strong></div><div><ul>'
    for reason in high_reasons:
        overview += f'<li>{reason}</li>'

    overview += '</ul></div></div><div class="report-metric-card"><div><strong>Identified Low Privacy Risk</strong></div><div><ul>'

    for reason in low_reasons:
        overview += f'<li>{reason}</li>'

    overview += '</ul></div></div></div>'

    with open('report/glossary.html', "r") as glossary_file:
        glossary = glossary_file.read()

    report_data = {"configDescription": overview, "glossar": glossary}
    return JSONResponse(content=report_data, status_code=200)


@app.get("/actuator/health")
async def health_check():
    status = {"status": "UP"}
    return JSONResponse(content=status, status_code=200)
