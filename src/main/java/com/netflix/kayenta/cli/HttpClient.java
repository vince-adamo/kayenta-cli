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

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpClient {

  protected String doGet(String url, Map<String, String> requestParameters) throws Exception {
    
    // append request parameters to url
    StringBuilder builder = new StringBuilder(url);
    int param = 0;
    for (String key : requestParameters.keySet()) {
      if (++param == 1)
        builder.append("?");
      else
        builder.append("&");
      builder.append(key).append("=").append(requestParameters.get(key));
    }
    URL obj = new URL(builder.toString());
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

    //add request header
    con.setRequestMethod("GET");

    // Send get request
    /*
    con.setDoOutput(true);
    DataOutputStream wr = new DataOutputStream(con.getOutputStream());
    wr.writeBytes(requestBody);
    wr.flush();
    wr.close();
    */

    //int responseCode = con.getResponseCode();

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

    String inputLine;
    StringBuffer response = new StringBuffer();
    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    return response.toString();

  }

  protected String doPost(String url, Map<String, String> requestParameters, String requestBody) throws Exception {
    
    // append request parameters to url
    StringBuilder builder = new StringBuilder(url);
    int param = 0;
    for (String key : requestParameters.keySet()) {
      if (++param == 1)
        builder.append("?");
      else
        builder.append("&");
      builder.append(key).append("=").append(requestParameters.get(key));
    }
    URL obj = new URL(builder.toString());
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

    //add request header
    con.setRequestMethod("POST");
    con.setRequestProperty("Content-Type", "application/json");

    // Send post request
    con.setDoOutput(true);
    DataOutputStream wr = new DataOutputStream(con.getOutputStream());
    wr.writeBytes(requestBody);
    wr.flush();
    wr.close();

    //int responseCode = con.getResponseCode();

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

    String inputLine;
    StringBuffer response = new StringBuffer();
    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    return response.toString();

  }
  
}
