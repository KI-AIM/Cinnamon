package org.bihmi.jal.anon.util;

import org.bihmi.jal.config.HierarchyConfig;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.aggregates.HierarchyBuilder;
import org.deidentifier.arx.aggregates.HierarchyBuilderIntervalBased;
import org.deidentifier.arx.aggregates.HierarchyBuilderRedactionBased;

import java.util.*;

public class Hierarchy {

    private final DataType<?> dataType;
    private final boolean retainDataType;
    String attributeName;
    HierarchyConfig config;

    HierarchyBuilder builder;

    Data data;

    public Hierarchy(Data data, String attributeName) {
        this(data, attributeName, false);
    }

    public Hierarchy(Data data, String attributeName, boolean retainDatatype) {
        this.attributeName = attributeName;
        this.data = data;
        this.dataType = data.getDefinition().getDataType(attributeName);
        this.retainDataType = retainDatatype;

        // TODO: automatically detect suitable protection mechanisms based on dataType and if dataType should be retained
    }

    public Hierarchy(Data data, HierarchyConfig config, boolean retainDatatype){
        this.config = config;
        this.attributeName = config.getAttributeName();
        this.data = data;
        this.dataType = data.getDefinition().getDataType(attributeName);
        this.retainDataType = retainDatatype;
        this.config = config;
    }

    public HierarchyBuilder createHierarchy(){
        checkBefore();
        HierarchyBuilder builder = selectAndCreateHierarchy(config);
        checkAfter();
        return null;
    }

    private HierarchyBuilder selectAndCreateHierarchy(HierarchyConfig config) {
        if (config == null){
            throw new RuntimeException("Hierarchy Config should not be null.");
        }
        
        switch (config.getHierarchyType()){
            case "INTERVALS" -> {
                return createWithFixedIntervalSize();
            }
            case "MASKING" -> {
                return createWithMasking();
            }
            case "ORDERING" -> {
                return createWithSet();
            }
//            case "DATES" -> {
//                return createForDates();
//            }
            default -> throw new IllegalStateException("Unexpected value: " + config.getHierarchyType());
        }
    }

    // TODO: need to switch to set aggregation 
    private HierarchyBuilder createWithSet() {
        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        int maxLength = 0;
        HashSet<Character> uniqueChars = new HashSet<>();

        for (String s : values) {
            if (s.length() > maxLength) {
                maxLength = s.length();
            }
            for (char c : s.toCharArray()) {
                uniqueChars.add(c);
            }
        }

        int alphabetSize = uniqueChars.size();  // number of unique characters
        int maxValueLength = maxLength;  // maximum length of protected word

        HierarchyBuilderRedactionBased<?> builder = HierarchyBuilderRedactionBased.create(HierarchyBuilderRedactionBased.Order.RIGHT_TO_LEFT,
                HierarchyBuilderRedactionBased.Order.RIGHT_TO_LEFT,
                ' ',
                '*');

        builder.setAlphabetSize(alphabetSize, maxValueLength);

        builder.prepare(values);
        builder.build();

        return builder;
    }

    protected HierarchyBuilder createWithMasking() {

        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        int maxLength = 0;
        HashSet<Character> uniqueChars = new HashSet<>();

        for (String s : values) {
            if (s.length() > maxLength) {
                maxLength = s.length();
            }
            for (char c : s.toCharArray()) {
                uniqueChars.add(c);
            }
        }

        int alphabetSize = uniqueChars.size();  // number of unique characters
        int maxValueLength = maxLength;  // maximum length of protected word

        HierarchyBuilderRedactionBased<?> builder = HierarchyBuilderRedactionBased.create(HierarchyBuilderRedactionBased.Order.RIGHT_TO_LEFT,
                HierarchyBuilderRedactionBased.Order.RIGHT_TO_LEFT,
                ' ',
                '*');

        builder.setAlphabetSize(alphabetSize, maxValueLength);

        builder.prepare(values);
        builder.build();

        return builder;
    }

    protected HierarchyBuilder createWithFixedIntervalSize() {
        HierarchyBuilder hierarchyBuilder = createIntervalBuilderByType(dataType, config.getIntervalSize());
        hierarchyBuilder.build();
        return hierarchyBuilder;
    }

    private HierarchyBuilder createIntervalBuilderByType(DataType<?> dataType, String intervalSize) {

        if (dataType.equals(DataType.DECIMAL)) {
            double size = Double.parseDouble(intervalSize);
            return decimalIntervalBuilder(size);
        } else if (dataType.equals(DataType.INTEGER)) {
            int size = Integer.parseInt(intervalSize);
            return integerIntervalBuilder(size);
        }
        throw new IllegalStateException("Unexpected value: " + dataType);
    }

    private HierarchyBuilderIntervalBased integerIntervalBuilder(int intervalRange) {
        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        long[] int_values = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == "NULL"){
                continue;
            }
            int_values[i] = Long.parseLong(values[i]);
        }

        int_values = Arrays.stream(int_values).sorted().toArray();
        long minValue = int_values[0];
        long maxValue = int_values[int_values.length - 1] +1;


        HierarchyBuilderIntervalBased<Long> hierarchyBuilder = HierarchyBuilderIntervalBased.create(
                DataType.INTEGER,
                new HierarchyBuilderIntervalBased.Range<Long>(minValue, minValue, minValue),
                new HierarchyBuilderIntervalBased.Range<Long>(maxValue, maxValue, maxValue));
        if (retainDataType) {
            hierarchyBuilder.setAggregateFunction(DataType.INTEGER.createAggregate().createArithmeticMeanFunction());
        } else {
            hierarchyBuilder.setAggregateFunction(DataType.INTEGER.createAggregate().createIntervalFunction());
        }

        hierarchyBuilder.addInterval(minValue, minValue + intervalRange);

        hierarchyBuilder.prepare(values);
        return hierarchyBuilder;
    }

    private HierarchyBuilderIntervalBased decimalIntervalBuilder(double intervalRange) {
        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        double[] double_values = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == "NULL"){
                continue;
            }
            double_values[i] = Double.parseDouble(values[i]);
        }

        double_values = Arrays.stream(double_values).sorted().toArray();
        double minValue = Math.floor(double_values[0]);
        double maxValue = Math.ceil(double_values[double_values.length - 1]) + 0.1;


        HierarchyBuilderIntervalBased<Double> hierarchyBuilder = HierarchyBuilderIntervalBased.create(
                DataType.DECIMAL,
                new HierarchyBuilderIntervalBased.Range<Double>(minValue, minValue, minValue),
                new HierarchyBuilderIntervalBased.Range<Double>(maxValue, maxValue, maxValue));
        if (retainDataType) {
            hierarchyBuilder.setAggregateFunction(DataType.DECIMAL.createAggregate().createArithmeticMeanFunction());
        } else {
            hierarchyBuilder.setAggregateFunction(DataType.DECIMAL.createAggregate().createIntervalFunction());
        }

        hierarchyBuilder.addInterval(minValue, minValue + intervalRange);

        hierarchyBuilder.prepare(values);
        return hierarchyBuilder;
    }

    private void checkBefore(){

    }

    private void checkAfter(){

    }

    public static Hierarchy fromCSV(String csvString) {
        // TODO
        return null; // new Hierarchy();
    }

    public static Hierarchy fromCSV(List<String> csvString) {
        // TODO
        return null; // new Hierarchy();
    }

    public static Hierarchy fromJson(String jsonString) {
        // TODO
        return null; // new Hierarchy();
    }

    public static Hierarchy fromYaml(String yamlString) {
        // TODO
        return null; // new Hierarchy();
    }
}
