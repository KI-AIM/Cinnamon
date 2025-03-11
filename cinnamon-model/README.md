# Cinnamon-Model

The Cinnamon-Model module contains all commonly used model classes as well as related utility like serialization and validation for the Cinnamon platform.
Other projects can use these classes for integrating the platform's API or to function as an external processing server.
This module is meant to be used for Spring projects and uses annotations from Spring's ecosystem to simplify the integration.

## Set-Up

Spring's validation and default deserialization works without any further configuration.
To ensure a consistent serialization format across all applications, this module also provides ObjectMapper for JSON and YAML serialization.
Using one of those mapper requires to declare the corresponding bean.
Be aware, that Spring only supports one ObjectMapper bean.
If the application should support JSON and YAML, a more advanced logic must be implemented.

In order to enable Spring's injection for all components provided by this module, the component scan annotation can be used.
```java
@ComponentScan({"de.kiaim.cinnamon.model.helper"})
```

## Feature Overview

### Model classes for datasets

The package [`de.kiaim.cinnamon.model.data`](./src/main/java/de/kiaim/cinnamon/model/data/DataSet.java) contains the internal representation of datasets.
The class [`DataSet`](./src/main/java/de/kiaim/cinnamon/model/data/DataSet.java) can be used to transform datasets in JSON format into an object-oriented representation.

### Model classes for configurations

Classes representing configurations can be found in the [`de.kiaim.cinnamon.model.configuration`](./src/main/java/de/kiaim/cinnamon/model/configuration) package.
The class [`DataConfiguration`](./src/main/java/de/kiaim/cinnamon/model/configuration/data/DataConfiguration.java) contains the metadata of the dataset.
The class [`FrontendAnonConfig`](./src/main/java/de/kiaim/cinnamon/model/configuration/anonymization/frontend/FrontendAnonConfig.java) contains the configuration for the cinnamon anonymization module.
Note that the platform does not enforce or validate the structure of configurations used by external processing servers.

### Response DTOs
The package `de.kiaim.cinnamon.model.dto` contains model classes for the communication between servers.

### Serialization
Serializing and deserializing of the model classes is done by custom serializer classes.
Spring's default serialization settings will use the custom serializers without any further configuration.

To ensure a consistent serialization format across all applications, this module also provides ObjectMapper for JSON and
YAML in the [`de.kiaim.cinnamon.model.serialization.mapper`](./src/main/java/de/kiaim/cinnamon/model/serialization/mapper) package.

### Validation
Most of the validation is done with annotations provided by Jakarta.
Additionally, custom validation annotations can be found in the package [`de.kiaim.cinnamon.model.validation`](./src/main/java/de/kiaim/cinnamon/model/validation).
To activate Spring's validation mechanism, use `@Valid` or `@Validated` annotations on the parameters.
