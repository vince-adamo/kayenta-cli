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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Main {

  static final long ONE_HOUR_IN_MILLIS = (60 * 60 * 1000); 
  static final String DEFAULT_KAYENTA_URL = "http://localhost:8090/canary"; 
  
  static String requestConfigFilename = AdhocRequestConfig.DEFAULT_FILENAME;
  
  static String kayentaURL = DEFAULT_KAYENTA_URL;
  static String metricsAccount = "";
  static String storageAccount = "";

  static Date analysisEndTime = null; 
  static Date analysisStartTime = null;
  
  static boolean verbose = false;
  
  /**
   * Print help statement for the program.
   * 
   */
  public static void printHelp() {
    StringBuilder builder = new StringBuilder(System.lineSeparator());
    builder.append("Kayenta client program arguments: ").append(System.lineSeparator()).append(System.lineSeparator());
    builder.append("-u url The Kayenta server URL (defaults to ").append(DEFAULT_KAYENTA_URL).append(")").append(System.lineSeparator());
    builder.append("-m metricAccount The name of the metric account (defaults to empty string)").append(System.lineSeparator());
    builder.append("-s storageAccount The name of the storage account (defaults to empty string)").append(System.lineSeparator());
    builder.append("-r filename The name of the request configuration file (defaults to ").append(requestConfigFilename).append(")").append(System.lineSeparator());
    builder.append("-t0 \"yyyy-MM-dd HH:mm:ss\" The analysis start time (defaults to local time zone, 1 hour ago)"+System.lineSeparator());
    builder.append("-t1 \"yyyy-MM-dd HH:mm:ss\" The analysis end time. (defaults to local time zone, now)"+System.lineSeparator());
    builder.append("-? print this help message").append(System.lineSeparator());
    System.out.println(builder.toString());
  }
  
  /**
   * Parse command line arguments.
   * 
   * @param args
   */
  public static void parseArgs(String[] args) {

    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    String argument = "";
    for (String arg : args) {
      if (argument.length() > 0) {
        // "arg" contains the previous arguments parameter
        if (argument.equals("r")) {
          requestConfigFilename = arg;
        } else if (argument.equals("u")) {
          kayentaURL = arg;
        } else if (argument.equals("m")) {
          metricsAccount = arg;
        } else if (argument.equals("s")) {
          storageAccount = arg;
        } else if (argument.equals("t0")) {
          try {
            analysisStartTime = dateFormatter.parse(arg);
          } catch (ParseException e) {
            System.out.println("An error was encountered while parsing the analysis start time argument.");
            printHelp();
            System.exit(1);
          }
        } else if (argument.equals("t1")) {
          try {
            analysisEndTime = dateFormatter.parse(arg);
          } catch (ParseException e) {
            System.out.println("An error was encountered while parsing the analysis start end argument.");
            printHelp();
            System.exit(1);
          }
        }
        argument = "";
      } else if (arg.startsWith("-")) {
        argument = arg.substring(1);
        // process arguments that don't have parameters here...
        if (argument.equals("?")) {
          printHelp();
          System.exit(0);
        } else if (argument.equals("v")) {
        	verbose = true;
        	argument = "";
        }
      }
    }

    if (analysisStartTime == null) {
      // default start time to 1 hour ago
      Date now = new Date();
      analysisStartTime = new Date(now.getTime() - (ONE_HOUR_IN_MILLIS));  
    }

    if (analysisEndTime == null) {
      // default end time to 1 hour after start time
      analysisEndTime = new Date(analysisStartTime.getTime() + (ONE_HOUR_IN_MILLIS));
    }
    
  }

  /**
   * Main routine that executes the Kayenta canary client request.
   *  
   * @param args
   */
  public static void main(String[] args) {
    
    parseArgs(args);
    
    Client client = new Client();

    try {
      
      System.out.println("sending the adhoc request to the server...");
      client.sendAdhocRequest(requestConfigFilename, kayentaURL, metricsAccount, storageAccount, 
                              analysisStartTime.getTime(), analysisEndTime.getTime(), verbose);
      
      System.out.print("waiting for the request to complete..."); // note the use of print vs println,
      client.waitForRequestToComplete();                          // the call to waitForRequestToComplete prints a newline before returning
      
      System.out.println("logging the request execution status...");
      client.logExecutionStatus();

      System.out.println("done.");
      
    } catch (RuntimeException ex) {
      System.out.println(ex.getMessage());
      System.exit(2);
    }
    
    System.exit(0);
    
  }

}
