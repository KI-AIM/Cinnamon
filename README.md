# KI-AIM

This is the main repository for the [KI-AIM](https://www.forschung-it-sicherheit-kommunikationssysteme.de/projekte/ki-aim) project.
A platform for providing anonymized data by combining anonymization and synthetization methods.

## Submodules

This project contains the following submodules.
For more information, read the README.md of the submodules.

### KI-AIM-Platform

The KI-AIM platform provides data management as well as the front end of the project.

## Build & Run
To build the entire project with all submodules, run

```bash
mvn clean install
```

You can also build only specific submodules by executing the command in the directory of the submodule.
The README.md of the submodules will also provide additional instructions.

## Deployment

For deploying the entire project, a docker compose file is provided.

To build all images, run

```bash
docker-compose build
```

> [!WARNING]
> Please change the password of the PostgreSQL user before deploying.
