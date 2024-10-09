/*
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.;
 */

package org.bihmi.jal.anon;

import lombok.Getter;
import org.deidentifier.arx.DataHandle;

import java.util.*;

/**
 * Class that retrieves and stores statistics of the anonymization.
 */
@Getter
public class AnonymizationResults {

    HashMap<String, Double> datasetPrivacyMetrics;
    // HashMap<String, Float> datasetUtilityMetrics;

    HashMap<String, HashMap<String, Double>> attributePrivacyMetrics;

    String name;
    // HashMap<String, HashMap<String, Float>> attributeUtilityMetrics;


    // Set<String> attributes;

    public AnonymizationResults(DataHandle handle){
        this.datasetPrivacyMetrics = fillDatasetPrivacyMetrics(handle);
        this.attributePrivacyMetrics = fillAttributePrivacyMetrics(handle);
    }

    public AnonymizationResults(DataHandle originalHandle, DataHandle anonymizedHandle){
        this.datasetPrivacyMetrics = fillDatasetPrivacyMetrics(originalHandle, "_original");
        this.datasetPrivacyMetrics.putAll(fillDatasetPrivacyMetrics(anonymizedHandle, "_anonymized"));
        this.attributePrivacyMetrics = fillAttributePrivacyMetrics(originalHandle, "_original");
        this.attributePrivacyMetrics = append_hashmap(this.attributePrivacyMetrics, fillAttributePrivacyMetrics(anonymizedHandle, "_anonymized"));
    }

    private HashMap<String, HashMap<String, Double>> append_hashmap(HashMap<String, HashMap<String, Double>> originalMetrics, HashMap<String, HashMap<String, Double>> anonymizedMetrics) {
        for (String key : anonymizedMetrics.keySet()) {
            HashMap<String, Double> origignalValues = originalMetrics.get(key);
            HashMap<String, Double> anonValues = anonymizedMetrics.get(key);

            origignalValues.putAll(anonValues);
        }
        return originalMetrics;
    }

    /**
     * Retrieves attribute statistics from handle and saves them.
     * @param handle anonymized data
     */
    private HashMap<String, HashMap<String, Double>> fillAttributePrivacyMetrics(DataHandle handle) {
        return fillAttributePrivacyMetrics(handle, "");
    }

    private HashMap<String, HashMap<String, Double>> fillAttributePrivacyMetrics(DataHandle handle, String suffix) {
        attributePrivacyMetrics = new HashMap<>();

        Set<String> qids = handle.getDefinition().getQuasiIdentifyingAttributes();
        for (String qid : qids) {
            HashMap<String, Double> singleAttributePrivacyMetrics = new HashMap<>();

            singleAttributePrivacyMetrics.put("Missings" + suffix,
                    handle.getStatistics().getQualityStatistics().getMissings().getValue(qid));
            singleAttributePrivacyMetrics.put("Granularity" + suffix,
                    handle.getStatistics().getQualityStatistics().getGranularity().getValue(qid));
            singleAttributePrivacyMetrics.put("NonUniformEntropy" + suffix,
                    handle.getStatistics().getQualityStatistics().getNonUniformEntropy().getValue(qid));

            attributePrivacyMetrics.put(qid, singleAttributePrivacyMetrics);
        }
        return attributePrivacyMetrics;
    }

    private HashMap<String, Double> fillDatasetPrivacyMetrics(DataHandle handle) {
        return fillDatasetPrivacyMetrics(handle, "");
    }

    /**
     * Retrieves dataset statistics from handle and saves them.
     * @param handle anonymized data
     */
    private HashMap<String, Double> fillDatasetPrivacyMetrics(DataHandle handle, String suffix) {
        if (handle==null){
            throw new RuntimeException("ARX DataHandle should not be null.");
        }

        datasetPrivacyMetrics = new HashMap<>();

        datasetPrivacyMetrics.put("JournalistRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getEstimatedJournalistRisk());

        datasetPrivacyMetrics.put("MinimumRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getLowestRisk());

        datasetPrivacyMetrics.put("RecordsAffectedByLowestRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getNumRecordsAffectedByLowestRisk());

        datasetPrivacyMetrics.put("HighestRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getHighestRisk());

        datasetPrivacyMetrics.put("RecordsAffectedByHighestRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getNumRecordsAffectedByHighestRisk());

        datasetPrivacyMetrics.put("AverageRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getAverageRisk());

        datasetPrivacyMetrics.put("MarketerRisk" + suffix,
                handle.getRiskEstimator().getSampleBasedReidentificationRisk().getEstimatedMarketerRisk());

        datasetPrivacyMetrics.put("MaxClassSize" + suffix,
                (double) handle.getStatistics().getEquivalenceClassStatistics().getMaximalEquivalenceClassSize());

        datasetPrivacyMetrics.put("AverageClassSize" + suffix,
                handle.getStatistics().getEquivalenceClassStatistics().getAverageEquivalenceClassSize());

        datasetPrivacyMetrics.put("MinClassSize" + suffix,
                (double) handle.getStatistics().getEquivalenceClassStatistics().getMinimalEquivalenceClassSize());

        datasetPrivacyMetrics.put("SuppressedRecords" + suffix,
                (double) handle.getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords());

        datasetPrivacyMetrics.put("Granularity" + suffix,
                handle.getStatistics().getQualityStatistics().getGranularity().getArithmeticMean());

        datasetPrivacyMetrics.put("Discernibility" + suffix,
                handle.getStatistics().getQualityStatistics().getDiscernibility().getValue());

        datasetPrivacyMetrics.put("Entropy" + suffix,
                handle.getStatistics().getQualityStatistics().getNonUniformEntropy().getArithmeticMean());
        return datasetPrivacyMetrics;
    }

    public List<String> getDatasetPrivacyHeader(){
        return new ArrayList<>(datasetPrivacyMetrics.keySet());
    }

    public List<Double> getDatasetPrivacyMetricValues(){
        return new ArrayList<>(datasetPrivacyMetrics.values());
    }

    public List<Double> getAttributePrivacyMetricValues(String attributeName, List<String> metricNames){
        List<Double> returnValue = new ArrayList<>();
        for (String key: metricNames) {
            returnValue.add(attributePrivacyMetrics.get(attributeName).get(key));
        }
        return returnValue;
    }

    public List<Double> getDatasetPrivacyMetricValues(List<String> metricNames){
        List<Double> returnValue = new ArrayList<>();
        for (String key: metricNames) {
            returnValue.add(datasetPrivacyMetrics.get(key));
        }
        return returnValue;
    }

    private List<String> getSortedMetrics(HashMap<String, HashMap<String, Double>> metrics) {

        List<String> metric_list = new ArrayList<>(metrics.values().stream().findAny().get().keySet());
        Collections.sort(metric_list);

        return metric_list;
    }

    // Note: derzeitige annahme dass alle attribute die gleichen metriken haben
    public List<String> getAttributePrivacyHeader() {
        return getSortedMetrics(attributePrivacyMetrics);
    }

}
