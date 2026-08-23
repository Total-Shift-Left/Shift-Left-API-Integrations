package io.jenkins.plugins.shiftleft;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class ShiftLeftTestPackBuilderIntegrationTest {

  @Rule
  public JenkinsRule jenkins = new JenkinsRule();

  private HttpServer server;
  private int port;
  private final AtomicBoolean triggered = new AtomicBoolean(false);
  private final AtomicInteger statusCallsAfterTrigger = new AtomicInteger(0);

  @Before
  public void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    port = server.getAddress().getPort();

    // Minimal JSON API stub for our plugin client.
    server.createContext("/api/v1/login", json((ex) -> {
      // Always succeed
      return "{\"token\":\"test-token\"}";
    }));

    server.createContext("/api/v1/test-packs/pack_1/run", json((ex) -> {
      triggered.set(true);
      return "{\"message\":\"started\",\"executionId\":\"exec_trigger\",\"packId\":\"pack_1\",\"status\":\"RUNNING\",\"startTime\":\"2026-01-01T00:00:00Z\"}";
    }));

    // The plugin probes status before triggering, records that executionId as the previous run, and
    // then refuses to gate until status reports a different one — that is the whole point of the
    // stale-results fix. So this stub has to actually advance: pexec_1 until the run is triggered,
    // exec_trigger afterwards. Serving pexec_1 throughout (as it used to) makes the plugin wait
    // correctly and the build time out, which is a passing plugin and a failing test.
    server.createContext("/api/v1/test-packs/pack_1/status", json((ex) -> {
      if (!triggered.get()) {
        return "{\"packId\":\"pack_1\",\"name\":\"Pack 1\",\"status\":\"COMPLETED\",\"executionId\":\"pexec_1\",\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-01-01T00:01:00Z\",\"summary\":{\"total\":2,\"passed\":2,\"failed\":0,\"error\":0,\"successRate\":100}}";
      }
      // One RUNNING poll first, so the wait loop is exercised rather than short-circuited.
      if (statusCallsAfterTrigger.incrementAndGet() == 1) {
        return "{\"packId\":\"pack_1\",\"name\":\"Pack 1\",\"status\":\"RUNNING\",\"executionId\":\"exec_trigger\",\"startTime\":\"2026-01-01T00:02:00Z\",\"summary\":{\"total\":2,\"passed\":0,\"failed\":0,\"error\":0,\"successRate\":0}}";
      }
      return "{\"packId\":\"pack_1\",\"name\":\"Pack 1\",\"status\":\"COMPLETED\",\"executionId\":\"exec_trigger\",\"startTime\":\"2026-01-01T00:02:00Z\",\"endTime\":\"2026-01-01T00:03:00Z\",\"summary\":{\"total\":2,\"passed\":2,\"failed\":0,\"error\":0,\"successRate\":100}}";
    }));

    server.createContext("/api/v1/test-packs/pack_1/results", json((ex) -> {
      String q = ex.getRequestURI().getQuery();
      // Results belong to the execution the plugin waited for, not the previous one.
      return "{\"packId\":\"pack_1\",\"packName\":\"Pack 1\",\"executionId\":\"exec_trigger\",\"startTime\":\"2026-01-01T00:02:00Z\",\"endTime\":\"2026-01-01T00:03:00Z\",\"summary\":{\"total\":2,\"passed\":2,\"failed\":0,\"error\":0,\"successRate\":100},\"results\":[{\"testId\":\"t1\",\"testName\":\"Test 1\",\"status\":\"PASSED\",\"duration\":1200,\"responseTime\":200},{\"testId\":\"t2\",\"testName\":\"Test 2\",\"status\":\"PASSED\",\"duration\":800,\"responseTime\":150}]}";
    }));

    server.start();
  }

  @After
  public void tearDown() {
    if (server != null) server.stop(0);
  }

  @Test
  public void successfulPackMarksBuildSuccessAndWritesSummary() throws Exception {
    // Add credentials
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds",
        "ShiftLeft credentials",
        "user@example.com",
        "password"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-creds");
    b.setPackId("pack_1");
    b.setWaitForCompletion(true);
    b.setPollIntervalSeconds(1);
    b.setTimeoutMinutes(1);
    b.setPassThresholdPercent(100.0);
    b.setFailOnErrorTests(true);
    b.setGateFailureResult("FAILURE");
    b.setWriteJsonSummary(true);
    b.setJsonSummaryPath("shiftleft-summary.json");
    p.getBuildersList().add(b);

    FreeStyleBuild build = jenkins.buildAndAssertSuccess(p);
    assertEquals(Result.SUCCESS, build.getResult());

    // Action attached
    ShiftLeftTestPackAction action = build.getAction(ShiftLeftTestPackAction.class);
    assertNotNull(action);
    assertEquals("pack_1", action.getSummary().getPackId());
    assertEquals("COMPLETED", action.getSummary().getLatestStatus());

    // JSON summary written
    assertTrue(build.getWorkspace().child("shiftleft-summary.json").exists());
  }

  private static HttpHandler json(BodySupplier supplier) {
    return new HttpHandler() {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
        String body = supplier.get(exchange);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(bytes);
        }
      }
    };
  }

  @FunctionalInterface
  private interface BodySupplier {
    String get(HttpExchange exchange) throws IOException;
  }
}

