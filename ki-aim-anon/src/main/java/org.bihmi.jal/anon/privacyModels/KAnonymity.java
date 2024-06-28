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

package org.bihmi.jal.anon.privacyModels;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.deidentifier.arx.Data;
import org.deidentifier.arx.criteria.PrivacyCriterion;

import java.util.Collection;
import java.util.Collections;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class KAnonymity extends PrivacyModel {

    /** Parameter for the privacy model */
    private int k;

    @Override
    public Collection<PrivacyCriterion> getPrivacyCriterion(Data data) {
        return Collections.singletonList(new org.deidentifier.arx.criteria.KAnonymity(k));
    }
}
