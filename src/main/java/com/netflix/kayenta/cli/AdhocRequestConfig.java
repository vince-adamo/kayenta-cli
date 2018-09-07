/*
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.cli;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class AdhocRequestConfig {
  
  public static final String DEFAULT_FILENAME = "./adhoc-request.json";
  
  @Data
  public static class ClientCanaryScope {
    private String location;
    private Map<String, String> extendedScopeParams; 
  }
  
  @Data
  public static class CanaryConfigClassifier {
    private Map<String, Double> groupWeights;
    private Map<String, Double> scoreThresholds;
  }
  
  @Data
  public static class MetricGroup {
    private Map<String, Map> analysisConfigurations;
    private List<String> groupByFields;
    private String customFilter;
    private String customFilterTemplate;
    private String groupName;
    private String serviceType;
    private List<String> metricNames;
  }

  // overall request thresholds
  private Map<String, Double> requestThresholds;
  
  // scope pair configuration
  private String scopeName;
  private ClientCanaryScope controlScope;
  private ClientCanaryScope experimentScope;

  // metric configuration
  private String name;
  private String judge;
  private Map<String, String> templates;
  private CanaryConfigClassifier classifier;
  private List<MetricGroup> metricGroups;
  
}
