# Cinnamon Anonymization Module

## Overview
The **Anonymization Module** is responsible for dataset anonymization. The module is based on the **[ARX library](https://arx.deidentifier.org/)**. The module communicates with the platform via an API to process anonymization requests. The data is anonymized according to the configuration defined by the user in the platform.

## Build & Run

### Docker
To build and run the module with Docker, you can follow the instruction mention in the **[Cinnamon Repository README](../README.md)**.

## Usage
1. Ensure the **platform Docker environment** is set up.
2. The module is automatically launched when the platform starts.
3. Data anonymization requests can be sent via the platform’s interface or API.

## Requirements
- **Docker**
- **ARX Library** (included in the container)
- **Platform Docker Setup** (refer to the platform’s main repository for setup instructions)

