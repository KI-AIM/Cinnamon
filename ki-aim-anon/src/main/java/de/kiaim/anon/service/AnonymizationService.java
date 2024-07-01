package de.kiaim.anon.service;

import de.kiaim.anon.config.AnonymizationConfig;
import de.kiaim.anon.converter.DatasetAnonConfigConverter;
import de.kiaim.anon.processor.AnonymizedDatasetProcessor;
import de.kiaim.model.configuration.anonymization.DatasetAnonymizationConfig;
import de.kiaim.anon.processor.DataSetProcessor;
import de.kiaim.model.configuration.data.DataConfiguration;
import de.kiaim.model.data.DataSet;
import org.bihmi.jal.anon.Anonymizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static de.kiaim.anon.service.CompatibilityAssurance.checkDataSetCompatibility;

@Service
public class AnonymizationService {
//    Interface to the platform that receive data and proceed anonymization
//    TODO :
//     - check dataset and configuration
//     - transform the configs into JAL configs object
//     - transform the dataset (if necessary) including the dataset configuration
//     - start the anonymization process with JAL
//     within JAL :
//          - check dataset and config again
//          - create hierarchies
//          - transform dates
//          - perform anonymization using ARX
//          - give back anonymized dataset and performance metrics
//     - analyze results (QualityAssurance)
//     - give back anonymized dataset and performance metrics

    @Autowired
    private DataSetProcessor dataSetProcessor;

    @Autowired
    private DatasetAnonConfigConverter datasetAnonConfigConverter;

    protected boolean checkDataConfiguration(DataSet dataset, DataConfiguration config, AnonymizationConfig anonConfig){
        // check if configuration and dataset is consistent, will probably be already made when creating the dataset object
        // check if any attributes are always NaN (null)
        // check if any datatype is included, that can not be supported yet
        checkDataSetCompatibility(dataset);
        return false;
    }

    public DataSet anonymizeData(DataSet dataSet, DatasetAnonymizationConfig datasetAnonymizationConfig) throws Exception {
        // TODO : add quality check mechanism

        // Convert KI-AIM DatasetAnonymizationConfig to AnonymizationConfig usable by JAL
        AnonymizationConfig anonymizationConfigConverted = datasetAnonConfigConverter.convert(datasetAnonymizationConfig, dataSet);

        // Convert KI-AIM DataSet object to String[][] usable by JAL
        String[][] jalData = dataSetProcessor.convertDatasetToStringArray(dataSet);

        // TODO : add mechanism to make anon name variable

        Anonymizer anonymizer = new Anonymizer(jalData, anonymizationConfigConverted.toJalConfig("AnonymizationJALV0"));
        anonymizer.anonymize();

        // TODO : add quality assurance, built dataSet object from String[]

        return AnonymizedDatasetProcessor.convertToDataSet(anonymizer.AnonymizedData(), dataSet.getDataConfiguration());
    }



}
