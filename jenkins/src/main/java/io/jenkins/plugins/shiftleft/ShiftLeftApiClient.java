package io.jenkins.plugins.shiftleft;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;

final class ShiftLeftApiClient {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Matches Azure DevOps extension id pattern: {@code publisher.product}. */
  private static final String USER_AGENT_PRODUCT_ID = "totalshiftleft.shift-left-api-automation-integration";

  /** Jenkins plugin short name (Maven {@code artifactId}). */
  private static final String PLUGIN_SHORT_NAME = "shift-left-api-automation-integration";

  private final String baseUrl;
  private final @CheckForNull String tenantId;
  private final TaskListener listener;
  private final HttpClient http;

  private @CheckForNull String bearerToken;

  ShiftLeftApiClient(@NonNull String baseUrl, @CheckForNull String tenantId, @NonNull TaskListener listener) {
    this.baseUrl = stripTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    this.tenantId = tenantId == null || tenantId.trim().isEmpty() ? null : tenantId.trim();
    this.listener = Objects.requireNonNull(listener, "listener");
    this.http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)  // Force HTTP/1.1 to match curl behavior
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  void setBearerToken(String token) {
    this.bearerToken = token;
  }

  String login(String email, String password) throws IOException, InterruptedException {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("email", email);
    body.put("password", password);

    String bodyJson = body.toString();
    listener.getLogger().println("[ShiftLeft] Login request body: " + bodyJson.replaceAll("\"password\":\"[^\"]+\"", "\"password\":\"***\""));

    HttpRequest req = baseRequest("/api/v1/login")
        .timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
        .build();

    JsonNode json = sendJson(req);
    String token = json.path("token").asText(null);
    if (token == null || token.trim().isEmpty()) {
      throw new IOException("Login succeeded but token was missing in response");
    }
    return token;
  }

  TriggerResponse triggerTestPack(String packId) throws IOException, InterruptedException {
    String encPackId = urlEncodePathSegment(packId);
    HttpRequest req = authedRequest("/api/v1/test-packs/" + encPackId + "/run")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    JsonNode json = sendJson(req);
    TriggerResponse r = new TriggerResponse();
    r.message = json.path("message").asText(null);
    r.executionId = json.path("executionId").asText(null);
    r.packId = json.path("packId").asText(null);
    r.status = json.path("status").asText(null);
    r.startTimeIso = json.path("startTime").asText(null);
    return r;
  }

  StatusResponse getStatus(String packId) throws IOException, InterruptedException {
    String encPackId = urlEncodePathSegment(packId);
    HttpRequest req = authedRequest("/api/v1/test-packs/" + encPackId + "/status")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    JsonNode json = sendJson(req);

    StatusResponse r = new StatusResponse();
    r.packId = json.path("packId").asText(null);
    r.name = json.path("name").asText(null);
    r.status = json.path("status").asText(null);
    r.executionId = json.path("executionId").asText(null);
    r.startTimeIso = json.path("startTime").asText(null);
    r.endTimeIso = json.path("endTime").asText(null);

    JsonNode summary = json.path("summary");
    r.summaryTotal = summary.path("total").asInt(0);
    r.summaryPassed = summary.path("passed").asInt(0);
    r.summaryFailed = summary.path("failed").asInt(0);
    r.summaryError = summary.path("error").asInt(0);
    r.summarySuccessRate = summary.path("successRate").asDouble(0.0);
    return r;
  }

  ResultsResponse getResults(String packId, @CheckForNull String executionId) throws IOException, InterruptedException {
    String encPackId = urlEncodePathSegment(packId);
    String path = "/api/v1/test-packs/" + encPackId + "/results";
    if (executionId != null && !executionId.trim().isEmpty()) {
      path = path + "?executionId=" + urlEncodeQuery(executionId.trim());
    }

    HttpRequest req = authedRequest(path)
        .timeout(Duration.ofSeconds(120))
        .GET()
        .build();
    JsonNode json = sendJson(req);

    ResultsResponse r = new ResultsResponse();
    r.packId = json.path("packId").asText(null);
    r.packName = json.path("packName").asText(null);
    r.executionId = json.path("executionId").asText(null);
    r.startTimeIso = json.path("startTime").asText(null);
    r.endTimeIso = json.path("endTime").asText(null);

    JsonNode summary = json.path("summary");
    if (!summary.isMissingNode() && summary.isObject()) {
      r.summaryTotal = summary.path("total").isMissingNode() ? null : summary.path("total").asInt(0);
      r.summaryPassed = summary.path("passed").isMissingNode() ? null : summary.path("passed").asInt(0);
      r.summaryFailed = summary.path("failed").isMissingNode() ? null : summary.path("failed").asInt(0);
      r.summaryError = summary.path("error").isMissingNode() ? null : summary.path("error").asInt(0);
      r.summarySuccessRate = summary.path("successRate").isMissingNode() ? null : summary.path("successRate").asDouble(0.0);
    }

    JsonNode results = json.path("results");
    if (results.isArray()) {
      for (JsonNode node : results) {
        ResultItem item = new ResultItem();
        item.testId = node.path("testId").asText(null);
        item.testName = node.path("testName").asText(null);
        item.status = node.path("status").asText(null);
        item.duration = node.path("duration").asLong(0L);
        item.responseTime = node.path("responseTime").asLong(0L);
        item.error = node.path("error").asText(null);
        item.startTimeIso = node.path("startTime").asText(null);
        item.endTimeIso = node.path("endTime").asText(null);
        r.results.add(item);
      }
    }
    r.resultsCount = r.results.size();
    return r;
  }

  List<TestPackItem> listTestPacks() throws IOException, InterruptedException {
    HttpRequest req = authedRequest("/api/v1/test-packs")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    JsonNode json = sendJson(req);
    List<TestPackItem> items = new ArrayList<>();
    if (json.isArray()) {
      for (JsonNode n : json) {
        TestPackItem item = new TestPackItem();
        item.packId = n.path("packId").asText(null);
        item.name = n.path("name").asText(null);
        item.projectName = n.path("projectName").asText(null);
        item.environment = n.path("environment").asText(null);
        item.lastStatus = n.path("lastStatus").asText(null);
        items.add(item);
      }
    }
    return items;
  }

  private HttpRequest.Builder baseRequest(String path) {
    return HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + path))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("User-Agent", userAgent());
  }

  private static String userAgent() {
    Jenkins j = Jenkins.getInstanceOrNull();
    if (j == null) {
      return USER_AGENT_PRODUCT_ID + "/unknown";
    }
    PluginWrapper w = j.getPluginManager().getPlugin(PLUGIN_SHORT_NAME);
    if (w == null) {
      return USER_AGENT_PRODUCT_ID + "/unknown";
    }
    return USER_AGENT_PRODUCT_ID + "/" + w.getVersion();
  }

  private HttpRequest.Builder authedRequest(String path) throws IOException {
    HttpRequest.Builder b = baseRequest(path);
    if (tenantId != null) {
      b.header("X-Tenant-ID", tenantId);
    }
    if (bearerToken == null || bearerToken.trim().isEmpty()) {
      throw new IOException("No bearer token set; did you call login()?");
    }
    b.header("Authorization", "Bearer " + bearerToken);
    return b;
  }

  private JsonNode sendJson(HttpRequest req) throws IOException, InterruptedException {
    // Log request details for debugging
    listener.getLogger().println("[ShiftLeft] Request: " + req.method() + " " + req.uri());
    if (req.headers().firstValue("Content-Type").isPresent()) {
      listener.getLogger().println("[ShiftLeft] Content-Type: " + req.headers().firstValue("Content-Type").get());
    }
    if (req.headers().firstValue("Accept").isPresent()) {
      listener.getLogger().println("[ShiftLeft] Accept: " + req.headers().firstValue("Accept").get());
    }
    
    HttpResponse<String> resp;
    try {
      resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      listener.getLogger().println("[ShiftLeft] Network error calling " + req.uri() + ": " + e.getMessage());
      throw new IOException("Network error calling " + req.uri() + ": " + e.getMessage(), e);
    }
    
    int code = resp.statusCode();
    String body = resp.body();
    
    // Log response details
    listener.getLogger().println("[ShiftLeft] Response: HTTP " + code);
    if (resp.headers().firstValue("Content-Type").isPresent()) {
      listener.getLogger().println("[ShiftLeft] Response Content-Type: " + resp.headers().firstValue("Content-Type").get());
    }
    
    if (code < 200 || code >= 300) {
      listener.getLogger().println("[ShiftLeft] HTTP " + code + " for " + req.uri());
      listener.getLogger().println("[ShiftLeft] Response body: " + truncate(body, 2000));
      
      // Provide specific guidance for 502 errors
      if (code == 502) {
        String errorMsg = "HTTP 502 Bad Gateway calling " + req.uri() + 
            ". This usually indicates the proxy/server cannot reach the backend. " +
            "Check server logs and ensure the backend service is running. " +
            "Request was: " + req.method() + " " + req.uri();
        throw new IOException(errorMsg);
      }
      
      throw new IOException("HTTP " + code + " calling " + req.uri());
    }
    try {
      return MAPPER.readTree(body == null ? "" : body);
    } catch (Exception e) {
      listener.getLogger().println("[ShiftLeft] Failed to parse JSON from " + req.uri());
      listener.getLogger().println("[ShiftLeft] Response: " + truncate(body, 2000));
      throw new IOException("Invalid JSON from " + req.uri() + ": " + e.getMessage(), e);
    }
  }

  private static String stripTrailingSlash(String s) {
    String out = s.trim();
    while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
    return out;
  }

  private static String urlEncodeQuery(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String urlEncodePathSegment(String s) {
    // RFC3986-ish encoding for path segments
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String truncate(@CheckForNull String s, int max) {
    if (s == null) return "";
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }

  static final class TriggerResponse {
    @CheckForNull String message;
    @CheckForNull String executionId;
    @CheckForNull String packId;
    @CheckForNull String status;
    @CheckForNull String startTimeIso;
  }

  static final class StatusResponse {
    @CheckForNull String packId;
    @CheckForNull String name;
    @CheckForNull String status;
    @CheckForNull String executionId;
    @CheckForNull String startTimeIso;
    @CheckForNull String endTimeIso;
    int summaryTotal;
    int summaryPassed;
    int summaryFailed;
    int summaryError;
    double summarySuccessRate;
  }

  static final class ResultsResponse {
    @CheckForNull String packId;
    @CheckForNull String packName;
    @CheckForNull String executionId;
    @CheckForNull String startTimeIso;
    @CheckForNull String endTimeIso;
    @CheckForNull Integer summaryTotal;
    @CheckForNull Integer summaryPassed;
    @CheckForNull Integer summaryFailed;
    @CheckForNull Integer summaryError;
    @CheckForNull Double summarySuccessRate;
    int resultsCount;
    final List<ResultItem> results = new ArrayList<>();
  }

  static final class ResultItem {
    @CheckForNull String testId;
    @CheckForNull String testName;
    @CheckForNull String status;
    long duration;
    long responseTime;
    @CheckForNull String error;
    @CheckForNull String startTimeIso;
    @CheckForNull String endTimeIso;
  }

  static final class TestPackItem {
    @CheckForNull String packId;
    @CheckForNull String name;
    @CheckForNull String projectName;
    @CheckForNull String environment;
    @CheckForNull String lastStatus;
  }
}

