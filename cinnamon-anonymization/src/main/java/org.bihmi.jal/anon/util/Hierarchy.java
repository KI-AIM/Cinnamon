package org.bihmi.jal.anon.util;

import org.bihmi.jal.config.HierarchyConfig;
import org.deidentifier.arx.AttributeType;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.aggregates.HierarchyBuilder;
import org.deidentifier.arx.aggregates.HierarchyBuilderDate;
import org.deidentifier.arx.aggregates.HierarchyBuilderIntervalBased;
import org.deidentifier.arx.aggregates.HierarchyBuilderRedactionBased;
import org.w3c.dom.Attr;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.bihmi.jal.anon.util.DateTransformationHelper.reverseDateEncoding;

public class Hierarchy {

    private final DataType<?> dataType;
    private final boolean retainDataType;
    String attributeName;
    HierarchyConfig config;
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

    public AttributeType.Hierarchy createHierarchy(){
        checkBefore();
        AttributeType.Hierarchy builder = selectAndCreateHierarchy(config);
        checkAfter();
        return builder;
    }

    private AttributeType.Hierarchy selectAndCreateHierarchy(HierarchyConfig config) {
        System.out.println(config.toString());
        if (config == null){
            throw new RuntimeException("Hierarchy Config should not be null.");
        }

        System.out.println("Hierarchy Type:");
        System.out.println(config.getHierarchyType());

        switch (config.getHierarchyType()){
            case "INTERVALS" -> {
                return createWithFixedIntervalSize();
            }
            case "SUPPRESSION" -> {
                return createSuppression();
            }
            case "MASKING" -> {
                return createWithMasking();
            }
            case "ORDERING" -> {
                return createWithSet();
            }
            case "DATES" -> {
                return createForDates();
            }
            default -> throw new IllegalStateException("Unexpected value: " + config.getHierarchyType());
        }
    }

    private AttributeType.Hierarchy createSuppression() {
        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        AttributeType.Hierarchy.DefaultHierarchy _hierarchy = AttributeType.Hierarchy.create();
        for (int i=0; i<values.length; i++) {
            _hierarchy.add(values[i], "*");
        }
        return _hierarchy;
    }

    private AttributeType.Hierarchy createForDates() {

        HierarchyBuilderDate.Granularity granularity = null;
        DataType<Date> dateType = DataType.createDate(config.getDateFormat());
        switch (config.getIntervalSize()){
            case "week/year": {
                granularity = HierarchyBuilderDate.Granularity.WEEK_YEAR;
            }
            case "month/year": {
                granularity = HierarchyBuilderDate.Granularity.MONTH_YEAR;
            }
            case "quarter/year": {
                granularity = HierarchyBuilderDate.Granularity.QUARTER_YEAR;
            }
            case "year": {
                granularity = HierarchyBuilderDate.Granularity.YEAR;
            }
            case "decade": {
                granularity = HierarchyBuilderDate.Granularity.DECADE;
            }
        }

        // Create the builder
        var builder = HierarchyBuilderDate.create(dateType, granularity);

        var attribute_index = data.getHandle().getColumnIndexOf(attributeName);
        String[] values = data.getHandle().getDistinctValues(attribute_index);

        builder.prepare(values);

        String[][] _hierarchy = builder.build().getHierarchy();
        AttributeType.Hierarchy.DefaultHierarchy hierarchy = AttributeType.Hierarchy.create();

        for (int i=0; i<_hierarchy.length; i++) {
            hierarchy.add(_hierarchy[i][0], reverseDateEncoding(_hierarchy[i][1], granularity));
        }
        return hierarchy;
    }


    // TODO: need to switch to set aggregation 
    private AttributeType.Hierarchy createWithSet() {
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

        return addFullSuppressedColumn(builder.build());
    }

    protected AttributeType.Hierarchy createWithMasking() {

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

        return addFullSuppressedColumn(builder.build());
    }

    protected AttributeType.Hierarchy createWithFixedIntervalSize() {
        AttributeType.Hierarchy hierarchyBuilder = createIntervalBuilderByType(dataType, config.getIntervalSize());
        return hierarchyBuilder;
    }

    private AttributeType.Hierarchy createIntervalBuilderByType(DataType<?> dataType, String intervalSize) {

        if (intervalSize==null || intervalSize.isEmpty()){
            intervalSize = "2";  // TODO: maybe set before and replace this with exception.
        }
        if (dataType.equals(DataType.DECIMAL)) {
            double size = Double.parseDouble(intervalSize);
            return decimalIntervalBuilder(size);
        } else if (dataType.equals(DataType.INTEGER)) {
            int size = Integer.parseInt(intervalSize);
            return integerIntervalBuilder(size);
        }
        throw new IllegalStateException("Unexpected value: " + dataType);
    }

    private AttributeType.Hierarchy integerIntervalBuilder(int intervalRange) {
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
        return addFullSuppressedColumn(hierarchyBuilder.build());
    }

    private AttributeType.Hierarchy decimalIntervalBuilder(double intervalRange) {
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
        System.out.println("Maximum value in generalization hierarchy " + maxValue);


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
        return addFullSuppressedColumn(hierarchyBuilder.build());
    }

    private AttributeType.Hierarchy addFullSuppressedColumn(AttributeType.Hierarchy hierarchy){
        String[][] _hierarchy = hierarchy.getHierarchy();
        AttributeType.Hierarchy.DefaultHierarchy newHierarchy = AttributeType.Hierarchy.create();
        for (int i=0; i<_hierarchy.length; i++) {
            newHierarchy.add(_hierarchy[i][0], _hierarchy[i][1], "*");
        }
        return newHierarchy;
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

    static void printArray(String[][] array) {
        System.out.print("{");
        for (int j=0; j<array.length; j++){
            String[] next = array[j];
            System.out.print("{");
            for (int i = 0; i < next.length; i++) {
                String string = next[i];
                System.out.print("\"" + string + "\"");
                if (i < next.length - 1) {
                    System.out.print(",");
                }
            }
            System.out.print("}");
            if (j<array.length-1) {
                System.out.print(",\n");
            }
        }
        System.out.println("}");
    }

}
