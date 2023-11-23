package de.kiaim.platform;

import de.kiaim.platform.model.data.configuration.ColumnConfiguration;
import de.kiaim.platform.model.data.configuration.DataConfiguration;
import de.kiaim.platform.model.TransformationResult;
import de.kiaim.platform.model.data.DataType;
import de.kiaim.platform.model.data.configuration.DateFormatConfiguration;
import de.kiaim.platform.model.data.configuration.StringPatternConfiguration;
import de.kiaim.platform.processor.CommonDataProcessor;
import de.kiaim.platform.processor.CsvProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Tesfile {

    public static void main(String[] args) {
        CommonDataProcessor processor = new CsvProcessor();

        String csvData =
                """
               208589,Wilson Maggio,28.02.1994,no,23623.18 €
               650390,Tonisha Swift,1975-05-08,no,303.23 €
               452159,Bill Hintz,1987-05-172421,no,38.41 €
               730160,Nelia Heathcote,1959-02-03,yes,21.01 €
               614164,Ms. Chester Keebler,1982-02-20,N/A,158.79 €
               45961,Avery Romaguera,1978-09-11,no,78.10 €
               544734,Dr. Sergio Gleason,1961-04-24,N/A,2447.63 €
               920777,Katharyn Cremin,1990-05-03,no,849.66 €
               368543,Grover Rau DDS,1987-01-12,no,20.69 €
               512864,Yun Hirthe,1954-05-17,no,862.53 €
               740802,Sade Hyatt,1974-10-25,no,38.69 €
               727691,Nakesha Osinski PhD,1994-09-01,no,8583.35 €
               620008,Ms. Ismael Klein,1961-02-16,yes,67.29 €
               657469,Morgan Kub,1951-05-25,yes,8701.46 €
               210729,Shelli Volkman V,1984-09-24,no,47.03 €
               931041,Dick Miller,1994-08-24,no,62.50 €
               620341,Curt Grimes DVM,1982-12-26,no,Günther
               635971,Daniela Stracke,1984-06-04,N/A,29.21 €
               170877,Logan Parker,1960-01-19,no,683.55 €
               620241,Sergio Hoppe,1989-02-27,no,2538.86 €
               255846,Keva Boyer,1995-08-26,no,46.48 €
               213532,Miss Harrison Runolfsdottir,1956-04-23,no,63.73 €
               82598,Keven Altenwerth V,1950-03-03,N/A,21.39 €
               118160,Joaquin Kris,1960-03-14,no,733649.98 €
               89262,Claud Kutch,1968-03-19,no,31.79 €
                        """;

        DataConfiguration config = new DataConfiguration();

        config.setDataTypes(
            Arrays.asList(
                DataType.INTEGER,
                DataType.STRING,
                DataType.DATE,
                DataType.BOOLEAN,
                DataType.STRING
            )
        );

        DateFormatConfiguration dateFormatConfiguration = new DateFormatConfiguration();
        dateFormatConfiguration.setDateFormatter("yyyy-MM-dd");

        StringPatternConfiguration stringPatternConfiguration = new StringPatternConfiguration();
        stringPatternConfiguration.setPattern("[1-9][0-9]{1,9}[.][0-9]{2} [€]");

        ColumnConfiguration column1 = new ColumnConfiguration(
                0, "id", DataType.INTEGER, new ArrayList<>()
        );

        ColumnConfiguration column2 = new ColumnConfiguration(
                1, "name", DataType.STRING, new ArrayList<>()
        );

        ColumnConfiguration column3 = new ColumnConfiguration(
                2, "birthdate", DataType.DATE, List.of(dateFormatConfiguration)
        );

        ColumnConfiguration column4 = new ColumnConfiguration(
                3, "smoker", DataType.BOOLEAN, new ArrayList<>()
        );

        ColumnConfiguration column5 = new ColumnConfiguration(
                4, "price", DataType.STRING, List.of(stringPatternConfiguration)
        );

        config.setConfigurations(List.of(column1, column2, column3, column4, column5));

        TransformationResult result = processor.transformTwoDimensionalDataToDataSetAndValidate(csvData, config);

        System.out.println("debug");
    }
}
