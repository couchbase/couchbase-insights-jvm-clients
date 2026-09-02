# Migration Guide

> If you are migrating from `couchbase-analytics-client` to `couchbase-insights-client` please see [Migrating from Enterprise Analytics](MIGRATING-FROM_ENTERPRISE-ANALYTICS.md).

The Couchbase Operational Insights Java SDK (or "insights" SDK for short) is designed to work specifically with Couchbase Operational Insights.
It is a successor to the analytics API from the general Couchbase Java SDK, which we'll refer to here as the "general" SDK.

This section offers advice on how to migrate code from the general SDK ("before") to the Operational Insights SDK ("after").

## Class names

The insights SDK omits the "Analytics" prefix from several class names in favor of the more general "Query" prefix.

| Before              | After                         |
|---------------------|-------------------------------|
| `AnalyticsResult`   | `QueryResult`                 |
| `AnalyticsOptions`  | `QueryOptions`                |
| `AnalyticsMetaData` | `QueryMetadata` (lowercase d) |
| `AnalyticsWarning`  | `QueryWarning`                |

The general SDK's reactive API does not have a direct analogue in the base insights SDK.
An API for integrating with Project Reactor is available as an extension library.
Note the prefix "Reactive" changes to "Reactor".

| Before                | After (with extension library) |
|-----------------------|--------------------------------|
| `ReactiveQueryResult` | `ReactorQueryResult`           |

## Method names

| Before           | After                                    |
|------------------|------------------------------------------|
| `analyticsQuery` | `executeQuery` / `executeStreamingQuery` |


## Query options

With the general SDK, the caller creates an instance of `AnalayticsOptions` and configures it.
The general SDK expected you to pass positional parameters as a `JsonArray`, and named parameters as a `JsonObject`.

With the insights SDK, options are specified via a callback that modifies an instance of `QueryOptions` created by the SDK.
The insights SDK takes positional parameters as a `List`, and named parameters as a `Map`.

_Before:_

```java
AnalyticsResult result = generalCluster
    .analyticsQuery(
        "SELECT ? AS greeting",
        AnalyticsOptions.analyticsOptions()
            .readonly(true)
            .parameters(JsonArray.from("hello world"))
    );
```

_After:_

```java
QueryResult result = insightsCluster
    .executeQuery(
        "SELECT ? AS greeting",
        options -> options
            .readOnly(true) // uppercase "O"
            .parameters(List.of("hello world"))
        );
```

## JsonObject and JsonArray

These classes are present in both the general and insights SDKs, but in different packages.
The two versions have similar methods, but are not interchangeable.

The version to use with the insights SDK is in package `com.couchbase.insights.client.java.json`.

If you need to pass JSON between the insights and general SDKs, first convert the JSON to a byte array using the `toBytes()` method.
Then use the other SDK's version to parse the JSON using the `fromJson(byte[])` method.


## Converting row values

With the general SDK, query result rows are accessed by calling `AnalyticsResult.rowsAs(<type>)`.
This method returns a new list where each result row is mapped to an instance of the specified type.

The insights SDK represents result rows differently.
It introduces a new `Row` class that represents a single result row.
The `queryResult.rows()` method returns a `List<Row>`.
To convert a row to an instance of some type, call `row.as(<type>)`.
If null is a valid value, call `row.asNullable(<type>)` instead.

Unlike the general SDK, the insights SDK does not have a dedicated method for converting a row to a `JsonObject`.
Instead, use `row.as(JsonObject.class)`.
(Make sure to use the version of `JsonObject` from the insights SDK instead of the general SDK, otherwise the conversion will fail.)

_Before:_

```java
import com.couchbase.client.java.json.JsonObject;
```

```java
AnalyticsResult result = operationalCluster
    .analyticsQuery("SELECT 'hello world' AS greeting");

JsonObject obj = result.rowsAsObject().getFirst();
System.out.println(obj.getString("greeting"));
```

_After:_

```java
import com.couchbase.insights.client.java.json.JsonObject;
```

```java
QueryResult result = insightsCluster
    .executeQuery("SELECT 'hello world' AS greeting");
    
JsonObject obj = result.rows().getFirst().as(JsonObject.class);
System.out.println(obj.getString("greeting"));
```

## Streaming result rows

In the general SDK, the only way to stream result rows from the server is to use the Reactive API.
The insights SDK adds a safe and convenient way to stream results rows without the complexity of reactive programming.

_Before:_

```java
Mono<ReactiveAnalyticsResult> resultMono = operationalCluster.reactive()
    .analyticsQuery("SELECT RAW i FROM ARRAY_RANGE(0, 10) as i");

resultMono.flatMapMany(result -> result.rowsAs(Integer.class))
    .doOnNext(System.out::println)
    .blockLast();
```

_After:_

```java
insightsCluster.executeStreamingQuery(
    "SELECT RAW i FROM ARRAY_RANGE(0, 10) as i",
    row -> System.out.println(row.as(Integer.class))
);
```

To aid migration of existing reactive codebases, and to support integrations with other reactive components, the insights SDK has an optional extension library that adds support for Project Reactor.

```xml
<dependency>
    <groupId>com.couchbase.client</groupId>
    <artifactId>couchbase-insights-java-client-reactor</artifactId>
    <version>x.y.z</version>
</dependency>
```

_After (with Reactor extension library):_

```java
var reactor = ReactorQueryable.from(insightsClusterOrScope);

Mono<ReactorQueryResult> resultMono = reactor
    .executeQuery("SELECT RAW i FROM ARRAY_RANGE(0, 10) as i");

resultMono.flatMapMany(ReactorQueryResult::rows)
    .map(row -> row.as(Integer.class))
    .doOnNext(System.out::println)
    .blockLast();
```
