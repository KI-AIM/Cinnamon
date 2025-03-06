# Cinnamon

Cinnamon is an accessible data protection platform providing tools for anonymizing data by combining anonymization and synthetization methods.
It has been developed as part of the [KI-AIM](https://www.forschung-it-sicherheit-kommunikationssysteme.de/projekte/ki-aim) project.

## About

The key features of Cinnamon are:

- **Anonymization**: Cinnamon is a comprehensive platform designed to anonymize tabular data, particularly with medical
  data in mind. The platform offers a combination of anonymization and
  synthetization algorithms to achieve data privacy.


- **Privacy Evaluation**: Privacy evaluation features assess of deidentification risks, ensuring
  sensitive information remains protected. Additionally, the resemblance evaluation determines the similarity between
  the original and anonymized datasets, while machine learning utility evaluation ensures the usability of anonymized
  data for various learning models.


- **Technical Evaluation**: Cinnamon provides tools to analyze original and anonymized datasets by identifying issues such as missing values, invalid
  entries, and evaluating distributions statistical metrics.


- **Accessibility**: To make Cinnamon accessible for users without expertise in anonymization, the platform includes
  detailed explanations of its features and processes.

### Algorithms
Internally [ARX](https://arx.deidentifier.org) is used for the anonymization.
The platform supports the following transformations:
- Masking
- Generalization
- Deleteion
- Micro Aggregation

The following synthetization algorithms are available
  - Conditional Tabular GAN (CTGAN)
  - Tabular Variational Autoencoder (TVAE)
  - Bayesian Network
  - Adversarial Random Forest

### Supported Data formats
Cinnamon supports tabular data in these formats:
- CSV (Comma-Separated Values)
- XLSX (Microsoft Excel files)

Support for FHIR is planned in future releases.

### Outlook
Cinnamon is currently work-in-progress and has many upcoming features.
For this year the following features are planned
  - Support for FHIR
  - Generation of a report
  - Build in guidance and documentation
  - Separation into Standard and Expert mode


## Documentation
For the future detailed Documentation and an introduction video are planned.

## Installation
For deploying the entire project, a docker compose file is provided.

```bash
docker-compose up
```

You can also build only specific submodules by executing the command in the directory of the submodule.
The README.md of the submodules will also provide additional instructions.

### Configuration

> [!WARNING]
> Please change the password of the PostgreSQL user before deploying.

When changing the configuration of the database (e.g. the password) make sure to also change the configuration of the platform accordingly.

```yaml
services:
  cinnamon-db:
    environment:
      # Has to match the datasource settings of the cinnamon-platform container
      - POSTGRES_DB=cinnamon_db
      - POSTGRES_USER=cinnamon_user
      - POSTGRES_PASSWORD=changeme
    cinnamon-platform:
      environment:
        # Datasource has to match with the configuration of the cinnamon-db container
        SPRING_DATASOURCE_PASSWORD: changeme
        SPRING_DATASOURCE_URL: jdbc:postgresql://cinnamon-db:5432/cinnamon_db
        SPRING.DATASOURCE.USERNAME: cinnamon_user
```

When changing container names or ports, the configurations for the external server have to be changed as well.

```yaml
services:
  cinnamon-platform:
    environment:
      CINNAMON.EXTERNAL-SERVER.0.CALLBACK-HOST: cinnamon-platform
      CINNAMON.EXTERNAL-SERVER.0.URL-SERVER: http://cinnamon-anonymization:8080
```

## Technical Aspects

## Submodules

Cinnamon is divided into the following submodules.
For more information, read the README.md of the submodules.

### Cinnamon-Model

The [Cinnamon-Model](./cinnamon-model/README.md) submodule contains commonly used model classes like configurations.

### Cinnamon-Anonymization
Submodule providing an API for anonymizing a dataset.

Running the anonymization modules requires to install arx:
```bash
mvn install:install-file -Dfile=cinnamon-anonymization/src/main/resources/lib/libarx-3.9.1.jar -DgroupId=org.deidentifier -DartifactId=arx -Dversion=3.9.1 -Dpackaging=jar
```

### Cinnamon-Platform

The [Cinnamon-Platform](./cinnamon-platform/README.md) provides data management as well as the front end of the project.

### Cinnamon-Test

Submodule containing all tests for the other modules as well as test utilities.

### Cinnamon-Evaluation

### Cinnamon-Synthetization
