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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.canary.CanaryExecutionResponse;
import com.netflix.kayenta.canary.CanaryExecutionStatusResponse;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.results.CanaryAnalysisResult;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.canary.results.CanaryJudgeScore;
import com.netflix.kayenta.canary.results.CanaryResult;
import com.netflix.kayenta.config.KayentaConfiguration;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Client extends HttpClient {

  public static final long WAIT_TIMEOUT_IN_SECONDS = 300;

  ObjectMapper mapper = getObjectMapper();

  String kayentaURL;
  String metricsAccount;
  String storageAccount;
  
  String canaryExecutionId;
  
  CanaryExecutionStatusResponse canaryExecutionStatus;
  
  boolean verbose = false;
  
  public Client() {
    super();
  }

  /**
   * Send a Kayenta Adhoc HTTP request 
   * 
   * @param configFilename
   * @param analysisStartTime
   * @param analysisEndTime
   */
  public void sendAdhocRequest(String configFilename, String kayentaURL, String metricsAccount, String storageAccount, long analysisStartTime, long analysisEndTime, boolean verbose) {
    
    this.kayentaURL = kayentaURL;
    this.metricsAccount = metricsAccount;
    this.storageAccount = storageAccount;
    
    this.verbose = verbose;
    
    AdhocRequestBuilder adhocRequestBuilder = new AdhocRequestBuilder(mapper);

    String requestBody = "";
    try {
      requestBody = mapper.writeValueAsString(adhocRequestBuilder.build(configFilename, analysisStartTime, analysisEndTime));
      System.out.println(requestBody);
    } catch (JsonProcessingException ex) {
      throw new RuntimeException("Error parsing canary adhoc execution request, reason: "+ex.getMessage(),ex);
    }

    Map<String, String> requestParameters = new HashMap<String, String>();
    if (!StringUtils.isEmpty(metricsAccount)) requestParameters.put("metricsAccountName", metricsAccount);
    if (!StringUtils.isEmpty(storageAccount)) requestParameters.put("storageAccountName", storageAccount);
    
    try {
      String response = doPost(kayentaURL, requestParameters, requestBody);
      canaryExecutionId = mapper.readValue(response, CanaryExecutionResponse.class).getCanaryExecutionId(); 
    } catch (Exception ex) {
      throw new RuntimeException("Unable to complete POST request, reason: "+ex.getMessage());
    }
    
  }
  
  /**
   * Wait for the request to complete...
   * 
   */
  public void waitForRequestToComplete() {

    String url = kayentaURL + "/" + canaryExecutionId;

    Map<String, String> requestParameters = new HashMap<String, String>();
    if (!StringUtils.isEmpty(storageAccount)) requestParameters.put("storageAccountName", storageAccount);
    requestParameters.put("canaryExecutionId", canaryExecutionId);
    
    long countdown = WAIT_TIMEOUT_IN_SECONDS;
    long progressTimer = 5;

    boolean completed = false;
    while (!completed && countdown > 0) {      
      try {
        String response = doGet(url, requestParameters);
        canaryExecutionStatus = mapper.readValue(response, CanaryExecutionStatusResponse.class);
        completed = canaryExecutionStatus.getComplete();
      } catch (Exception ex) {
        completed = true;
        canaryExecutionStatus = 
            CanaryExecutionStatusResponse.builder()
            .complete(Boolean.FALSE)
            .status(ex.getMessage())
            .build();
      }
      if (!completed)
        try {
          Thread.sleep(1000);
          if (--progressTimer <= 0) {
            System.out.print(".");
            progressTimer = 5;
          }
          if (--countdown <= 0) {
            canaryExecutionStatus = 
                CanaryExecutionStatusResponse.builder()
                .complete(Boolean.FALSE)
                .status("timed out waiting for completion status")
                .build();
          }
        } catch (InterruptedException e) {
          // ignore
        }
    }

    System.out.println("");
    
  }
  
  /**
   * Log the completion status...
   * 
   */
  public void logExecutionStatus() {
        
    System.out.println("========== Canary Execution Status ==========");
    System.out.println("Status URL: "+kayentaURL + "/" + canaryExecutionId);
    if (canaryExecutionStatus != null) {
    	
      System.out.println("Complete: "+canaryExecutionStatus.getComplete().toString());
      System.out.println("Status: "+canaryExecutionStatus.getStatus());
      CanaryResult canaryResult = canaryExecutionStatus.getResult();
      if (canaryResult != null) {
        CanaryJudgeResult judgeResult = canaryResult.getJudgeResult();
        if (judgeResult != null) {
          CanaryJudgeScore judgeScore = judgeResult.getScore();
          System.out.println("Score: "+judgeScore.getScore());
          System.out.println("Grade: "+judgeScore.getClassification());
          String reason = judgeScore.getClassificationReason();
          if (!StringUtils.isEmpty(reason)) {
            System.out.println("Reason: "+judgeScore.getClassification());
          }
          if (verbose) {
	          List<CanaryAnalysisResult> results = judgeResult.getResults();
	          System.out.println("========== Passed Results Summary ===========");
	          int entry = 0;
	          for (CanaryAnalysisResult result : results) {
	        	  if (result.getClassification().equals("Pass")) {
	        		  if (entry++ > 0) System.out.println("-------------");
	        		  logResultEntry(result);
	        	  }
	          }
	          System.out.println("========== Failed Results Summary ===========");
	          entry = 0;
	          for (CanaryAnalysisResult result : results) {
	        	  if (!result.getClassification().equals("Pass")) {
	        		  if (entry++ > 0) System.out.println("-------------");
	        		  logResultEntry(result);
	        	  }
	          }
          }
        }
      }

    } else {
      System.out.println("Complete: false");
      System.out.println("Status: null");
    }
    
    System.out.println("=============================================");
    
  } 
  
  protected void logResultEntry(CanaryAnalysisResult result) {
	  System.out.println("Name: "+result.getName());
	  System.out.println("Experiment: "+result.getExperimentMetadata().toString());
	  System.out.println("Control:    "+result.getControlMetadata().toString());
	  System.out.println("Overall:    "+result.getResultMetadata().toString());
  }

  /**
   * Included the following methods from com.netflix.kayenta.retrofit.config.KayentaConfiguration to support the
   * correct de-serialization of 1) properties of type Instant and 2) subtypes of CanaryMetricSetQueryConfig.
   * These methods are not available to this implementation because it currently does not use spring boot framework and
   * therefore no "autowired" objectMapper is available.
   */  
  private ObjectMapperSubtypeConfigurer.ClassSubtypeLocator assetSpecSubTypeLocator() {
    return new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(CanaryMetricSetQueryConfig.class, ImmutableList.of("com.netflix.kayenta.canary.providers.metrics"));
  }
  
  private ObjectMapper getObjectMapper() {    
    ObjectMapper objectMapper = new ObjectMapper();
    new ObjectMapperSubtypeConfigurer(true).registerSubtypes(objectMapper, Collections.singletonList(assetSpecSubTypeLocator()));
    KayentaConfiguration.configureObjectMapperFeatures(objectMapper);
    return objectMapper;
  } 
}
