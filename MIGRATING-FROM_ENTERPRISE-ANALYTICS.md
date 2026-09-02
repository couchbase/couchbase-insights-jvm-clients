# Migrating from Enterprise Analytics

To migrate from `couchbase-analytics-java-client` to `couchbase-insights-java-client`:

1. Search for `com.couchbase.analytics` and replace with `com.couchbase.insights`

2. Search for `AnalyticsTimeoutException` and replace with `InsightsTimeoutException`

3. Search for `AnalyticsException` and replace with `InsightsException`
