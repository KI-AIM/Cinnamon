<h1 align="center">
  Cinnamon
</h1>

<div align="center">
  <img src="./cinnamon-platform/cinnamon-frontend/src/app/assets/cinnamon-logo.png" alt="Cinnamon logo" width="100">
</div>

<div align="center">

<a href="https://github.com/KI-AIM/Cinnamon/blob/main/LICENSE">
  <img alt="GitHub License" src="https://img.shields.io/badge/License-Apache%202.0-blue.svg">
</a>

<a href="https://github.com/ki-aim/cinnamon/actions/workflows/docker-push.yml">
  <img alt="GitHub License" src="https://github.com/ki-aim/cinnamon/actions/workflows/docker-push.yml/badge.svg">
</a>

</div>

Cinnamon is a modular application designed to offer robust functionalities for data anonymization, synthetization, and evaluation.

The platform has been developed as part of the [KI-AIM](https://www.forschung-it-sicherheit-kommunikationssysteme.de/projekte/ki-aim) project.

## Key-Features

- **Modular Framework**: Cinnamon's design makes it simple to add new features and functionalities. This modular
  approach ensures the platform can be customized to fit specific requirements.


- **Data Anonymization and Synthetization**: By incorporating methods for anonymizing and synthetizing data, Cinnamon
  helps protect sensitive information while still allowing for data use.


- **Comprehensive Evaluation Module**: The evaluation module provides clear, concise results, converting complex data
  protection processes into understandable insights.


- **Support for Various Data Formats**: Cinnamon handles multiple data formats, including CSV and Excel, and we're
  working to include support for medical formats like FHIR, enabling versatility across industries.


- **Guided Workflow**: Cinnamon offers guidance through complex data protection functions, making it accessible to users
  regardless of their experience level.

## Demonstration

A demonstration server is available at the following address: [http://cinnamon-demo.uni-muenster.de](http://cinnamon-demo.uni-muenster.de).
You can use the sample dataset and configurations provided in the [/sample](./sample/Heart_Disease_Dataset) directory of this repository.

## Getting Started with Cinnamon

The following video provides an overview of the Cinnamon Platform:

[![Getting Started with Cinnamon](https://img.youtube.com/vi/KQ0WHKMXXA8/0.jpg)](https://www.youtube.com/watch?v=KQ0WHKMXXA8)

## Quick Start
Cinnamon with all its modules can be installed with Docker Compose.
Clone the repository and run the following command in the root directory:

```bash
docker-compose up -d
```

The website is available at http://localhost:8080.

## Documentation
Detailed information about Cinnamon's features, configuration, and development is available in our [documentation](https://ki-aim.github.io/cinnamon-docs/).

## License
Cinnamon is open source published under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Acknowledgement
Supported by BMBF grant No. 16KISA115K
