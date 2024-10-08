package org.bihmi.jal.anon;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.anon.util.Hierarchy;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.HierarchyConfig;
import org.bihmi.jal.config.QualityModelConfig;
import org.bihmi.jal.enums.MicroAggregationFunction;
import org.deidentifier.arx.*;
import org.deidentifier.arx.criteria.PrivacyCriterion;
// import org.deidentifier.arx.exceptions.RollbackRequiredException;
import org.deidentifier.arx.io.CSVDataOutput;
import org.deidentifier.arx.metric.Metric;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Getter(AccessLevel.PROTECTED)
public class Anonymizer {

    private Data originalData;
    private DataHandle anonymizedData;
    private ARXConfiguration arxConfig;
    private JALConfig JALConfig;
    public AnonymizationResults anonymizationResult;

    public Anonymizer(String[][] data, JALConfig JALConfig) {
        // creates ARX Datahandle object
        try{
            this.originalData = Data.create(data);
        } catch (Exception e){
            e.printStackTrace();
            log.debug("ERROR when trying to create dataset");

            for (int n=0; n<data.length; n++) {
                for (int m=0; m<data[n].length; m++) {
                    System.out.print(data[n][m]);
                }
                System.out.print("\n");
            }

        }

        updateAnonConfig(JALConfig);
    }

    public Anonymizer(Data data, JALConfig JALConfig) {
        this.originalData = data;
        updateAnonConfig(JALConfig);
    }

    private void setDataType(AttributeConfig attributeConfig) {
        String name = attributeConfig.getName();
        String dataType = attributeConfig.getDataType();
        this.originalData.getDefinition().setDataType(name, DataTypeConverter.get(dataType));
    }

    private void setAttributeType(AttributeConfig attributeConfig) {
        String name = attributeConfig.getName();
        AttributeType attrType = attributeConfig.getArxAttributeType();
        this.originalData.getDefinition().setAttributeType(name, attrType);
    }

    public void updateAnonConfig(JALConfig config) {
        this.JALConfig = config;
        for (AttributeConfig attributeConfig : JALConfig.getAttributeConfigs()) {
            try {
                // set necessary information
                setDataType(attributeConfig);
                setAttributeType(attributeConfig);
                setHierarchy(attributeConfig);
                setGeneralizationLevelLimits(attributeConfig);  // TODO: for now disabled, only first level is created
                setMicroAggregation(attributeConfig);
            } catch (Exception e){
                e.printStackTrace();
                log.debug("Exception caught when trying to set the anon config");
            }

        }
    }

    public String[][] AnonymizedData(){

        List<String[]> resultingData = new ArrayList<>();
        Iterator<String[]> iterator = this.anonymizedData.iterator();
        while (iterator.hasNext()) {
            resultingData.add(iterator.next());
        }

        return resultingData.toArray(new String[resultingData.size()][]);
    }

    private void setGeneralizationLevelLimits(AttributeConfig attributeConfig) {
        HierarchyConfig hierarchyConfig = attributeConfig.getHierarchyConfig();

        if (hierarchyConfig == null) {
            return;
        }

        String name = attributeConfig.getName();

        if (hierarchyConfig.getMaxLevelToUse() != null) {
            this.originalData.getDefinition().setMaximumGeneralization(name, hierarchyConfig.getMaxLevelToUse());
        }
        if (hierarchyConfig.getMinLevelToUse() != null) {
            this.originalData.getDefinition().setMinimumGeneralization(name, hierarchyConfig.getMinLevelToUse());
        }
    }

    private void setMicroAggregation(AttributeConfig attributeConfig) {
        if (attributeConfig.getMicroAggregationFunction() == null) {
            return;
        }

        Boolean ignoreMissingData = attributeConfig.isIgnoreMissingData();
        Boolean performClustering = attributeConfig.isPerformClustering();
        AttributeType.MicroAggregationFunction type = AttributeType.MicroAggregationFunction.createGeometricMean(ignoreMissingData);

        switch (attributeConfig.getMicroAggregationFunction()) {
            case GEOMETRIC_MEAN -> type = AttributeType.MicroAggregationFunction.createGeometricMean(ignoreMissingData);
            case ARITHMETIC_MEAN -> type = AttributeType.MicroAggregationFunction.createArithmeticMean(ignoreMissingData);
            case MEDIAN -> type = AttributeType.MicroAggregationFunction.createMedian(ignoreMissingData);
            case INTERVAL -> type = AttributeType.MicroAggregationFunction.createInterval(ignoreMissingData);
            case SET -> type = AttributeType.MicroAggregationFunction.createSet(ignoreMissingData);
            case MODE -> type = AttributeType.MicroAggregationFunction.createMode(ignoreMissingData);
        }
        this.originalData.getDefinition().setMicroAggregationFunction(attributeConfig.getName(), type, performClustering);

    }

    private void setHierarchy(AttributeConfig attributeConfig) {
        HierarchyConfig hierarchyConfig = attributeConfig.getHierarchyConfig();

        if (hierarchyConfig == null) {
            return;
        }

        String name = attributeConfig.getName();
        Hierarchy hierarchy = new Hierarchy(this.originalData, hierarchyConfig, true);
        // TODO (KO): ^^^ retaining of Data Type should be derived from dedicated anon config entry
        this.originalData.getDefinition().setHierarchy(name, hierarchy.createHierarchy());
    }

    public void anonymize() throws IllegalStateException, RuntimeException {

        configureARX(this.JALConfig);

        try {
            log.info("Anonymizing " + JALConfig.getName());
            long startTime = System.currentTimeMillis();

            this.anonymizedData = runARX();

            long endTime = System.currentTimeMillis();
            log.info("Anonymization done in " + (endTime - startTime) + "ms");

            this.anonymizationResult = new AnonymizationResults(this.anonymizedData);
            log.info("AnonymizationResults created.");
        } catch (IllegalStateException e) {
            log.info("Illegal State Exception. Anonymization was not performed.");
            throw e;
        } catch (RuntimeException e) {
            log.info("Runtime Exception. Anonymization was not performed.");
            throw e;
        }
    }

    private ARXConfiguration setQualityModel(ARXConfiguration config, JALConfig JALConfig) {
        QualityModelConfig qualityModel = JALConfig.getQualityModel();
        if (qualityModel == null) {
            // use default configuration?
            qualityModel = new QualityModelConfig();
            qualityModel.setQualityModelType(QualityModelConfig.QualityModelType.LOSS_METRIC);
            qualityModel.setGsFactor(0.5);
            qualityModel.setAggregateFunction(Metric.AggregateFunction.GEOMETRIC_MEAN);
            // throw new IllegalStateException("Quality model was not set.");
        }

        Double gsFactor = qualityModel.getGsFactor();

        Metric.AggregateFunction aggregateFunction = qualityModel.getAggregateFunction();
        if (aggregateFunction == null) {
            aggregateFunction = Metric.AggregateFunction.GEOMETRIC_MEAN;
        }

        QualityModelConfig.QualityModelType qualityModelType = qualityModel.getQualityModelType();
        switch (qualityModelType) {
            case LOSS_METRIC:
                config.setQualityModel(Metric.createLossMetric(gsFactor, aggregateFunction));
                break;
            default:
                throw new RuntimeException("QualityModel not specified");
        }
        return config;
    }

    private void configureARX(JALConfig JALConfig) {

        ARXConfiguration config = ARXConfiguration.create();

        for (PrivacyModel privacyModel : JALConfig.getPrivacyModelList()) {
            for (PrivacyCriterion privacyCriterion : privacyModel.getPrivacyCriterion(this.originalData)) {
                config.addPrivacyModel(privacyCriterion);
            }
        }
        config.setAlgorithm(JALConfig.getAnonymizationAlgorithm());
        config.setSuppressionLimit(JALConfig.getSuppressionLimit());
        if (JALConfig.getDifferentialPrivacySearchBudget() != null) {
            config.setDPSearchBudget(JALConfig.getDifferentialPrivacySearchBudget());
        }
        if (JALConfig.getHeuristicSearchStepLimit() != null) {
            config.setHeuristicSearchStepLimit(JALConfig.getHeuristicSearchStepLimit());
        }
        if (JALConfig.getHeuristicSearchTimeLimit() != null) {
            config.setHeuristicSearchTimeLimit(JALConfig.getHeuristicSearchTimeLimit());
        }
        config = setQualityModel(config, JALConfig);
        this.arxConfig = config;
    }

    private DataHandle runARX() {

        // Anonymize
        ARXAnonymizer anonymizer = new ARXAnonymizer();
        try {
            ARXResult result = anonymizer.anonymize(this.originalData, this.arxConfig);
            DataHandle output = result.getOutput();
            if (JALConfig.isLocalGeneralization() && result.isResultAvailable()) {
                try {
                    // Define relative number of records to be generalized in each iteration
                    double oMin = 1d / (double) JALConfig.getLocalGeneralizationIterations();
                    result.optimizeIterativeFast(output, oMin);
                } catch (Exception e) {
                    System.out.println("!!!! ASSUMED LOCAL GENERALIZATION!!! ");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            return output;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("!!! general exception caught!!!");
            throw new RuntimeException(e);
        }
    }

}
