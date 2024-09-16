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

package org.bihmi.jal.config;

import lombok.*;
import org.bihmi.jal.enums.MicroAggregationFunction;
import org.deidentifier.arx.AttributeType;


/**
 * Attribute Config. Also contains code to convert levels to hierarchies.
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeConfig {

    private String name;
    private String dataType;
    private String attributeType;  // TODO (KO): needs better naming
    private String dateFormat; //if attribute is a Date, define format here, if null ARX Default is used "dd.MM.yyyy"
    private String[] possibleEntries; // if categorical, each possible value
    private Boolean is_nullable; //TODO: what is the function of this? -> could contain NULL values, not in use currently
    private Boolean include = true; // if false, attribute is excluded for almost everything
    private Object min; // min of all possible values (may exceed data range from sample)
    private Object max; // max of all possible values (may exceed data range from sample)
    private HierarchyConfig hierarchyConfig;
    private boolean useMicroAggregation = false; // if true, use micro aggregation for this attribute
    private MicroAggregationFunction microAggregationFunction; // = MicroAggregationFunction.ARITHMETIC_MEAN; // parameter for micro aggregation
    private boolean performClustering = true; // parameter for micro aggregation
    private boolean ignoreMissingData = true; // parameter for micro aggregation

    public AttributeType getArxAttributeType() {
        return switch (attributeType) {
            case "QUASI_IDENTIFYING_ATTRIBUTE" -> AttributeType.QUASI_IDENTIFYING_ATTRIBUTE;
            case "SENSITIVE_ATTRIBUTE" -> AttributeType.SENSITIVE_ATTRIBUTE;
            case "INSENSITIVE_ATTRIBUTE" -> AttributeType.INSENSITIVE_ATTRIBUTE;
            case "IDENTIFYING_ATTRIBUTE" -> AttributeType.IDENTIFYING_ATTRIBUTE;
            default -> throw new RuntimeException("Invalid Attribute type defined");
        };
    }

    @Override
    public String toString() {
        return "AttributeConfig{" +
                "name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", type='" + attributeType + '\'' +
                ", dateFormat='" + dateFormat + '\'' +
                ", is_nullable=" + is_nullable +
                ", include=" + include +
                ", min=" + min +
                ", max=" + max +
                ", hierarchyConfig='" + hierarchyConfig + '\'' +
                ", useMicroAggregation=" + useMicroAggregation +
                ", microAggregationFunction=" + microAggregationFunction +
                ", performClustering=" + performClustering +
                ", ignoreMissingData=" + ignoreMissingData +
                '}';
    }
}

