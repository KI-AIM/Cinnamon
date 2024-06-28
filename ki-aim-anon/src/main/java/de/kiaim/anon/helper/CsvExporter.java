package de.kiaim.anon.helper;

import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@Component
public class CsvExporter {

    /**
     * Exports the given dataset to a CSV file.
     *
     * @param dataset The dataset to be exported, String[][].
     * @param outputPath The path to the output CSV file.
     * @throws IOException If an I/O error occurs.
     */
    public static void exportToCsv(String[][] dataset, String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            for (String[] row : dataset) {
                writer.write(String.join(",", row));
                writer.newLine();
            }
            writer.flush();
        }
    }
}
