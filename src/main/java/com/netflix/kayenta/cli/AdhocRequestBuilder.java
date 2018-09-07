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

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.netflix.kayenta.canary.CanaryAdhocExecutionRequest;
import com.netflix.kayenta.canary.CanaryClassifierConfig;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryExecutionRequest;
import com.netflix.kayenta.canary.CanaryJudgeConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopePair;
import com.netflix.kayenta.canary.Metadata;
import com.netflix.kayenta.canary.providers.metrics.DatadogCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.PrometheusCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.spinnaker.kork.jackson.InvalidSubtypeConfigurationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdhocRequestBuilder {

  ObjectMapper mapper;
  NamedType[] metricProviders = null;
 
  public AdhocRequestBuilder(ObjectMapper mapper) {
    super();
    this.mapper = mapper;
  }

  /**
   * Build an instance of <code>CanaryAdhocExecutionRequest</code> from the request parameters.
   * 
   * @param adhocRequestConfigFilename
   * @param mapper
   * @param analysisStartTime
   * @param analysisEndTime
   * @return an Adhoc execution request.
   */
  public CanaryAdhocExecutionRequest build(String configFilename, long analysisStartTime, long analysisEndTime ) {

    AdhocRequestConfig adhocRequestConfig;
    try {
      adhocRequestConfig = mapper.readValue(new File(configFilename), AdhocRequestConfig.class);
    } catch (Exception ex) {
      String errorMessage = "An exception was encountered reading adhoc request configuration file "+configFilename;
      log.error(errorMessage, ex);
      throw new RuntimeException(errorMessage, ex);
    }

    String scopeName = adhocRequestConfig.getScopeName();

    // ======================
    // Build out CanaryConfig
    // ======================

    // ...set the name of the judge implementation to use

    CanaryJudgeConfig canaryJudgeConfig =
        CanaryJudgeConfig.builder()
        .name(adhocRequestConfig.getJudge())
        .build();
    

    // ...define referenced metric filter templates (https://www.spinnaker.io/guides/user/canary/config/filter_templates/)

    Map<String, String> templates = adhocRequestConfig.getTemplates();

    // ...Create metric groups and add metrics (https://www.spinnaker.io/guides/user/canary/config/#create-metric-groups-and-add-metrics)

    CanaryClassifierThresholdsConfig scoreThresholds = 
        CanaryClassifierThresholdsConfig.builder()
        .marginal(adhocRequestConfig.getClassifier().getScoreThresholds().get("marginal"))
        .pass(adhocRequestConfig.getClassifier().getScoreThresholds().get("pass"))
        .build();

    CanaryClassifierConfig.CanaryClassifierConfigBuilder builder = CanaryClassifierConfig.builder();
    for (String groupWeightKey : adhocRequestConfig.getClassifier().getGroupWeights().keySet()) {
      builder.groupWeight(groupWeightKey, adhocRequestConfig.getClassifier().getGroupWeights().get(groupWeightKey));
    }
    builder.scoreThresholds(scoreThresholds);
    CanaryClassifierConfig classifier = builder.build();

    // Metric Analysis Configurations:
    //
    // NaN Strategy - Remove (default) or replace NaN with Zero's (https://github.com/spinnaker/kayenta/issues/237#issue-298742965)
    // Direction Strategy - Increase, Decrease, Either (default) - when and why?
    // Critical Strategy - True or False (Default) - when and why?

    List<CanaryMetricConfig> canaryMetricConfigs = new ArrayList<>();

    for (AdhocRequestConfig.MetricGroup metricGroup : adhocRequestConfig.getMetricGroups()) {
      for (String metricName : metricGroup.getMetricNames()) {
        canaryMetricConfigs.add(getMetricConfig(scopeName, metricName, 
                                                metricGroup.getGroupName(),
                                                metricGroup.getServiceType(),
                                                metricGroup.getCustomFilter(),
                                                metricGroup.getCustomFilterTemplate(),
                                                metricGroup.getGroupByFields(),
                                                metricGroup.getAnalysisConfigurations()));
      }
    }

    // ...complete instance build

    long now = System.currentTimeMillis();

    CanaryConfig canaryConfig = 
        CanaryConfig.builder()
        .name(adhocRequestConfig.getName())
        .application(scopeName)
        .judge(canaryJudgeConfig)
        .metrics(canaryMetricConfigs)
        .templates(templates)
        .classifier(classifier)
        .createdTimestamp(now)
        .createdTimestampIso(Instant.ofEpochMilli(now).toString())
        .updatedTimestamp(now)
        .updatedTimestampIso(Instant.ofEpochMilli(now).toString())        
        .build();

    // ================================
    // Build out CanaryExecutionRequest
    // ================================

    CanaryExecutionRequest executionRequest = new CanaryExecutionRequest();

    // Set Metadata (not currently used)

    List<Metadata> metadataList = new ArrayList<Metadata>();
    executionRequest.setMetadata(metadataList);

    // Set Scopes - "Metric scope defines where, when, and on what the canary analysis occurs. 
    //               It describes the specific baseline and canary server groups, the start and end times and interval, and the cloud 
    //               resource on which the baseline and canary are running." (https://www.spinnaker.io/guides/user/canary/stage/#metric-scope)

    // ...first, create canary control scope

    CanaryScope controlScope = new CanaryScope();
    controlScope.setScope(adhocRequestConfig.getScopeName());
    controlScope.setLocation(adhocRequestConfig.getControlScope().getLocation());
    controlScope.setExtendedScopeParams(adhocRequestConfig.getControlScope().getExtendedScopeParams());
    controlScope.setStart(Instant.ofEpochMilli(analysisStartTime));
    controlScope.setEnd(Instant.ofEpochMilli(analysisEndTime));
    controlScope.setStep(60l); // step every 60 seconds

    // ...second, create canary experimental scope

    CanaryScope experimentScope = new CanaryScope();

    experimentScope.setScope(adhocRequestConfig.getScopeName());
    experimentScope.setLocation(adhocRequestConfig.getExperimentScope().getLocation());
    experimentScope.setExtendedScopeParams(adhocRequestConfig.getExperimentScope().getExtendedScopeParams());
    experimentScope.setStart(controlScope.getStart());
    experimentScope.setEnd(controlScope.getEnd());    
    experimentScope.setStep(controlScope.getStep());

    // ...third, add control and experimental scopes to executionRequest

    CanaryScopePair canaryScopePair = new CanaryScopePair();
    canaryScopePair.setControlScope(controlScope);
    canaryScopePair.setExperimentScope(experimentScope);

    Map<String, CanaryScopePair> scopes = new HashMap<String, CanaryScopePair>();
    scopes.put(scopeName, canaryScopePair);

    executionRequest.setScopes(scopes);

    // Set SiteLocal (not currently used)

    Map<String, Object> siteLocal = new HashMap<String, Object>();
    executionRequest.setSiteLocal(siteLocal);

    // Set Thresholds

    CanaryClassifierThresholdsConfig thresholds =
        CanaryClassifierThresholdsConfig.builder()
        .marginal(adhocRequestConfig.getRequestThresholds().get("marginal"))
        .pass(adhocRequestConfig.getRequestThresholds().get("pass"))
        .build();

    executionRequest.setThresholds(thresholds);

    // =====================================
    // Build out CanaryAdhocExecutionRequest
    // =====================================

    CanaryAdhocExecutionRequest adhocRequest = new CanaryAdhocExecutionRequest();
    adhocRequest.setCanaryConfig(canaryConfig);
    adhocRequest.setExecutionRequest(executionRequest);

    return adhocRequest;

  }

  /**
   * Get the CanaryMetricConfig instance for a specific Server Metric definition.
   * 
   * @param scopeName
   * @param metricName
   * @param metricGroup
   * @param filterName
   * 
   * @returna CanaryMetricConfig instance
   */
  private CanaryMetricConfig getMetricConfig(String scopeName, String metricName, String metricGroup, String serviceType, 
                                             String customFilter, String customFilterTemplate, List<String> groupByFields, 
                                             Map<String, Map> analysisConfigurations) {

    if (metricProviders == null) {
      metricProviders = findMetricProviders();
    }

    // TODO: There is most likely a more elegant way to do this...a pluggable interface would be nice...
    
    CanaryMetricSetQueryConfig query = null;
    for (NamedType metricProvider : metricProviders) {
      if (metricProvider.getName().equals(serviceType)) {
        Class<?> cls = metricProvider.getType();
        if (cls.getSimpleName().equals("DatadogCanaryMetricSetQueryConfig")) {
          query = DatadogCanaryMetricSetQueryConfig.builder()
              .metricName(metricName)
              .build();        
        } else if (cls.getSimpleName().equals("PrometheusCanaryMetricSetQueryConfig")) {
          query = PrometheusCanaryMetricSetQueryConfig.builder()
              .metricName(metricName)
              .groupByFields(groupByFields)
              .customFilter(customFilter)
              .customFilterTemplate(customFilterTemplate)
              .build();        
        } else if (cls.getSimpleName().equals("StackdriverCanaryMetricSetQueryConfig")) {
          query = StackdriverCanaryMetricSetQueryConfig.builder()
              .metricType(metricName)
              .groupByFields(groupByFields)
              .customFilter(customFilter)
              .customFilterTemplate(customFilterTemplate)
              .build();        
        } else {
          throw new RuntimeException("No CanaryMetricSetQueryConfig subtype was found for service type "+serviceType);
        }
      }
    }

    return CanaryMetricConfig.builder()
        .name(metricName)
        .scopeName(scopeName)
        .group(metricGroup)
        .analysisConfigurations(analysisConfigurations)
        .query(query)
        .build();

  }

  /**
   * The following method is taken from com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.findSubtypes, and supports
   * the mapping of metric provider types to implementation classes.
   */  
  private NamedType[] findMetricProviders() {
    
    List<NamedType> namedTypes = new ArrayList<NamedType>();

    Class<?> clazz = CanaryMetricSetQueryConfig.class;
    String pkg = "com.netflix.kayenta.canary.providers.metrics";

    ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(clazz));

    Set<BeanDefinition> beans = provider.findCandidateComponents(pkg);
    for (BeanDefinition bean : beans) {
      
      Class<?> cls = ClassUtils.resolveClassName(bean.getBeanClassName(), ClassUtils.getDefaultClassLoader());
      
      JsonTypeName nameAnnotation = cls.getAnnotation(JsonTypeName.class);
      if (nameAnnotation == null || "".equals(nameAnnotation.value())) {
        String message = "Subtype " + cls.getSimpleName() + " does not have a JsonTypeName annotation";
        throw new InvalidSubtypeConfigurationException(message);
      }
      
      namedTypes.add(new NamedType(cls, nameAnnotation.value()));

    }
    
    return namedTypes.toArray(new NamedType[0]);

  }

}
