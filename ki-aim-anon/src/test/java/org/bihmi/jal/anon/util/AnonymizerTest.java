package org.bihmi.jal.anon.util;

import de.kiaim.anon.converter.HierarchyConverter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bihmi.jal.anon.Anonymizer;
import org.bihmi.jal.anon.JALConfig;
import org.bihmi.jal.anon.privacyModels.KAnonymity;
import org.bihmi.jal.anon.privacyModels.PrivacyModel;
import org.bihmi.jal.config.AttributeConfig;
import org.bihmi.jal.config.HierarchyConfig;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.HierarchyBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AnonymizerTest {

    private Data loadDataset(String fileName) throws IOException {
        Data data = Data.create(fileName, Charset.defaultCharset(), ',');

        return data;
    }

    private List<PrivacyModel> createPrivacyModel(){
        KAnonymity kanonymity = new KAnonymity();
        kanonymity.setK(10);
        return Arrays.asList(kanonymity);
    }


    @Test
    void testAnonymizer_integerAnon() throws IOException {
        String filename = ".\\data\\heart.csv";
        Data data = loadDataset(filename);

        AttributeConfig.AttributeConfigBuilder builder = AttributeConfig.builder()
                .name("Age")
                .dataType("INTEGER")
                .attributeType("QUASI_IDENTIFYING_ATTRIBUTE")
                .hierarchyConfig(new HierarchyConfig("Age", "INTERVALS", "5", "", "", 1, 1, null));


        JALConfig jalConfig = new JALConfig();
        jalConfig.setName("Heart_test");
        jalConfig.setPrivacyModelList(createPrivacyModel());
        jalConfig.setAttributeConfigs(Arrays.asList(builder.build()));

        Anonymizer anonymizer = new Anonymizer(data, jalConfig);
        anonymizer.anonymize();
        String[][] strings = anonymizer.AnonymizedData();
        System.out.println(strings);
    }

    @Test
    void testAnonymizer_dateAnon() throws IOException {
        String filename = ".\\data\\heart.csv";
        Data data = loadDataset(filename);

        AttributeConfig.AttributeConfigBuilder builder = AttributeConfig.builder()
                .name("birthdate")
                .dataType("DATE")
                .dateFormat("yyyy-MM-dd")
                .attributeType("QUASI_IDENTIFYING_ATTRIBUTE")
                .hierarchyConfig(new HierarchyConfig("birthdate", "DATES", "decade", "yyyy-MM-dd", "", 1, 1, null));


        JALConfig jalConfig = new JALConfig();
        jalConfig.setName("Heart_test");
        jalConfig.setPrivacyModelList(createPrivacyModel());
        jalConfig.setAttributeConfigs(Arrays.asList(builder.build()));

        Anonymizer anonymizer = new Anonymizer(data, jalConfig);
        anonymizer.anonymize();
        String[][] strings = anonymizer.AnonymizedData();
        for (String[] row : strings) {
            for (String cell : row) {
                System.out.print(cell + " ");
            }
            System.out.println();
        }
    }



    private List<String> loadUniqueValues(String fileName, String column) throws IOException {
        Set<String> uniqueValues = new HashSet<>();
        try (Reader in = new FileReader(fileName, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.withDelimiter(',').withFirstRecordAsHeader().parse(in)) {
            for (CSVRecord record : parser) {
                uniqueValues.add(record.get(column));
            }
        }
        return new ArrayList<>(uniqueValues);
    }

    @Test
    void createHierarchyEqualSizes() throws IOException {

        String fileNameIn = ".\\data\\heart.csv";
        String column = "Age";

        List<String> column_data = loadUniqueValues(fileNameIn, column);
        Data data = Data.create(fileNameIn, Charset.defaultCharset(), ';');
        data.getDefinition().setDataType(column, DataType.DECIMAL);

        Hierarchy creator = new Hierarchy(data, column);
        HierarchyBuilder hierarchyBuilder = creator.createWithFixedIntervalSize();

    }
}
