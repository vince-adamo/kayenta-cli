# kayenta-cli

> This project provides a client interface for sending canary analysis execution requests to a remote Kayenta service. 

> As described below, this project is intended to be integrated with an existing [Kayenta](https://github.com/spinnaker/kayenta/) project and has been verified up through Kayenta tag v1.0.9.

### Notes:

* The original project this is based upon uses a custom developed metric provider, not one of the ones supported out-of-the-box by Kayenta. So, this project has not been tested with one of the supported providers. If interested, please feel free to test it out and document any observed issues (with suggested fixes would be better).


* Due to the use of certain lombok annotations for the specific metric config classes, these classes are hard coded to help with building the query element for the CanaryMetricConfig. There is most likely a more elegant approach, but until one has been implemented, you will have to add references to any custom providers in AdhocRequestBuilder. 


* As noted at the bottom of this README, there is an example adhoc-request.json file that should be used as a starting point. To reduce duplication of metric definitions, the concept of "metricGroups" was added to the request json. Metrics with similar elements such as analysis configuration, custom filters/templates, group names and service type can be defined together and associated with one of the groups defined in the "classifier" element. This json is converted into concrete Kayenta classes and then converted to a JSON request string for the actual REST API request.

## Integrating into Kayenta project:

- Clone this project into the top level Kayenta project source tree, e.g.:

```
~/dev$ git clone https://github.com/spinnaker/kayenta.git
~/dev$ cd kayenta
~/dev/kayenta$ git checkout v1.0.9
~/dev/kayenta$ git clone https://github.com/vince-adamo/kayenta-cli.git
```

- Add the following include statement to kayenta/settings.gradle :

```
include 'kayenta-cli'
```

- Modify CanaryExecutionResponse.java and CanaryExecutionStatusResponse (found in kayenta/src/main/java/com/netflix/kayenta/canary)
to include the @NoArgsConstructor and @AllArgsConstructor annotations (this may not be necessary on newer releases):

```
// before
@Data
@Builder
```

```
// after
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
```

- Build the project:

```
~/dev/kayenta$ cd kayenta-cli
~/dev/kayenta/kayenta-cli$ ../gradlew clean build
```

The result of the build is an executable jar file located in kayenta-cli/build/libs.

## Requesting a canary analysis execution:

The following is an example of how to execute a canary request using the jar that was built:

```
~/dev/kayenta/kayenta-cli$ java -jar build/libs/kayenta-cli-1.1.0-SNAPSHOT.jar -t0 "2018-07-24 06:00:00" -t1 "2018-07-24 07:00:00"
sending the adhoc request to the server...
waiting for the request to complete..........
logging the request execution status...
========== Canary Execution Status ==========
Status URL: http://localhost:8090/canary/01CK9NM5PW80YADXCVH6SEZBT0
Complete: true
Status: succeeded
Score: 100.0
Grade: Pass
=============================================
done.
```

NOTE: the start/end times are specified using the client's local time zone.


The following is an example of how to print help on using this client utility:

```
~/dev/kayenta/kayenta-cli$ java -jar build/libs/kayenta-cli-1.1.0-SNAPSHOT.jar -?

Kayenta client program arguments: 

-u url The Kayenta server URL (defaults to http://localhost:8090/canary)
-m metricAccount The name of the metric account (defaults to empty string)
-s storageAccount The name of the storage account (defaults to empty string)
-r filename The name of the request configuration file (defaults to ./adhoc-request.json)
-t0 "yyyy-MM-dd HH:mm:ss" The analysis start time (defaults to local time zone, 1 hour ago)
-t1 "yyyy-MM-dd HH:mm:ss" The analysis end time. (defaults to local time zone, now)
-? print this help message
```

A sample adhoc-request.json file is included as a starting place for defining requests.
