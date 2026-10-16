package io.jenkins.plugins.shiftleft;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.htmlunit.util.NameValuePair;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import org.jvnet.hudson.test.MockAuthorizationStrategy;

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

    // Backs the Pack ID dropdown. Registered after the more specific contexts above, which keep
    // their own paths: HttpServer dispatches on the longest matching prefix.
    server.createContext("/api/v1/test-packs", json((ex) -> "[{\"packId\":\"pack_1\",\"name\":\"Pack 1\",\"projectName\":\"Proj\",\"environment\":\"dev\",\"lastStatus\":\"COMPLETED\"}]"));

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

  @Test
  public void triggerOnlyRunRecordsTriggerOnlyDecision() throws Exception {
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds-trigger-only",
        "ShiftLeft credentials",
        "user@example.com",
        "password"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-creds-trigger-only");
    b.setPackId("pack_1");
    b.setWaitForCompletion(false);
    b.setWriteJsonSummary(true);
    b.setJsonSummaryPath("shiftleft-trigger-only.json");
    p.getBuildersList().add(b);

    FreeStyleBuild build = jenkins.buildAndAssertSuccess(p);

    ShiftLeftTestPackAction action = build.getAction(ShiftLeftTestPackAction.class);
    assertNotNull(action);
    // The shared ci-client runner reports TRIGGER_ONLY for a trigger-without-wait run, and
    // ci-client/README.md documents it as a gate decision code. A null decision is drift.
    assertEquals("TRIGGER_ONLY", action.getSummary().getDecision());
    assertEquals("exec_trigger", action.getSummary().getTriggerExecutionId());
    assertTrue(build.getWorkspace().child("shiftleft-trigger-only.json").exists());
    assertTrue(triggered.get());
  }

  @Test
  public void writeJUnitXmlDefaultsToTrueOnNewSteps() {
    // Matches write-test-results-xml in the shared ci-client runner.
    ShiftLeftTestPackBuilder fresh = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    assertTrue(fresh.isWriteJUnitXml());
  }

  @Test
  public void existingJobsKeepTheirPersistedWriteJUnitXmlValue() {
    // config.xml is loaded with XStream, which allocates the object without running the
    // constructor or the field initialisers and then sets the fields it finds in the XML. So an
    // existing job that persisted writeJUnitXml=false keeps false across this default flip.
    String xml = "<io.jenkins.plugins.shiftleft.ShiftLeftTestPackBuilder>"
        + "<serverUrl>http://localhost:" + port + "</serverUrl>"
        + "<packId>pack_1</packId>"
        + "<waitForCompletion>true</waitForCompletion>"
        + "<writeJsonSummary>true</writeJsonSummary>"
        + "<writeJUnitXml>false</writeJUnitXml>"
        + "</io.jenkins.plugins.shiftleft.ShiftLeftTestPackBuilder>";
    ShiftLeftTestPackBuilder restored =
        (ShiftLeftTestPackBuilder) Jenkins.XSTREAM2.fromXML(xml);
    assertFalse(restored.isWriteJUnitXml());
    assertTrue(restored.isWriteJsonSummary());

    // Same reason, the other way round: a config.xml that never carried the element does not
    // silently pick up the new default either - it gets the JVM default for the field.
    String withoutField = "<io.jenkins.plugins.shiftleft.ShiftLeftTestPackBuilder>"
        + "<serverUrl>http://localhost:" + port + "</serverUrl>"
        + "<packId>pack_1</packId>"
        + "</io.jenkins.plugins.shiftleft.ShiftLeftTestPackBuilder>";
    ShiftLeftTestPackBuilder legacy =
        (ShiftLeftTestPackBuilder) Jenkins.XSTREAM2.fromXML(withoutField);
    assertFalse(legacy.isWriteJUnitXml());
  }

  @Test
  public void openingAnExistingJobsConfigPageDoesNotFlipWriteJUnitXml() throws Exception {
    // config.jelly carries default="true" so that a build step being added for the first time
    // offers the box checked. That default must not leak into a job that has already persisted
    // false: the box has to render unchecked, and saving the page untouched has to keep false.
    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setPackId("pack_1");
    b.setWaitForCompletion(false);
    b.setWriteJUnitXml(false);
    p.getBuildersList().add(b);
    p.save();

    HtmlPage configPage = jenkins.createWebClient().getPage(p, "configure");
    HtmlForm form = configPage.getFormByName("config");
    HtmlCheckBoxInput box = form.getInputByName("_.writeJUnitXml");
    assertFalse("a persisted false must not render as checked", box.isChecked());

    jenkins.submit(form);
    ShiftLeftTestPackBuilder resaved = p.getBuildersList().get(ShiftLeftTestPackBuilder.class);
    assertFalse(resaved.isWriteJUnitXml());
  }

  @Test
  public void aPersonalCredentialIsNotResolvedForABuild() throws Exception {
    // perform() resolves the credential with findCredentialById alone. The fallbacks that call
    // replaced impersonated whoever triggered the build, so a job naming an id could borrow that
    // user's personal credentials. This configuration has to fail, and fail before the plugin
    // sends anything to the server.
    jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
    User alice = User.getById("alice", true);
    UsernamePasswordCredentialsImpl personal = new UsernamePasswordCredentialsImpl(
        CredentialsScope.USER,
        "shiftleft-personal",
        "Personal credentials of alice",
        "alice@example.com",
        "password"
    );
    try (ACLContext ignored = ACL.as2(alice.impersonate2())) {
      CredentialsStore personalStore = null;
      for (CredentialsStore store : CredentialsProvider.lookupStores(alice)) {
        if (store.getContext() == alice) {
          personalStore = store;
        }
      }
      assertNotNull("expected a personal credentials store for alice", personalStore);
      assertTrue(personalStore.addCredentials(Domain.global(), personal));
    }

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-personal");
    b.setPackId("pack_1");
    b.setWaitForCompletion(false);
    p.getBuildersList().add(b);

    FreeStyleBuild build = p.scheduleBuild2(0, new Cause.UserIdCause("alice")).get();
    jenkins.assertBuildStatus(Result.FAILURE, build);
    jenkins.assertLogContains("not readable by this job", build);
    assertFalse("no request may be sent when the credential does not resolve", triggered.get());
  }

  @Test
  public void aGlobalCredentialStillResolvesForABuild() throws Exception {
    // The other half of the above: the single authorization-aware lookup still resolves a
    // normally configured global credential, which is what every documented setup uses.
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds-global-only",
        "ShiftLeft credentials",
        "user@example.com",
        "password"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-creds-global-only");
    b.setPackId("pack_1");
    b.setWaitForCompletion(false);
    p.getBuildersList().add(b);

    jenkins.buildAndAssertSuccess(p);
    assertTrue(triggered.get());
  }

  @Test
  public void theBuildLogNeverCarriesAnyPartOfThePassword() throws Exception {
    // The login body used to be logged with the password regexed out of the serialized JSON,
    // which left the tail of a password containing a double quote in the log.
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds-quoted-password",
        "ShiftLeft credentials",
        "user@example.com",
        "ab\"cd"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-creds-quoted-password");
    b.setPackId("pack_1");
    b.setWaitForCompletion(false);
    p.getBuildersList().add(b);

    FreeStyleBuild build = jenkins.buildAndAssertSuccess(p);
    String log = String.join("\n", build.getLog(500));
    assertFalse("the password must not appear in the build log: " + log, log.contains("ab\"cd"));
    assertFalse("not even a fragment of it: " + log, log.contains("cd\""));
    assertFalse("and the request body has no business being logged: " + log,
        log.contains("\"password\""));
  }

  @Test
  public void thePackIdDropdownIsPostOnly() throws Exception {
    // doFillPackIdItems logs in to the configured server with the stored credential. Every form
    // method that makes an authenticated outbound request is POST-only - doTestConnection next to
    // it already was - so a GET must not reach it.
    FreeStyleProject p = jenkins.createFreeStyleProject();
    jenkins.createWebClient().assertFails(
        p.getUrl() + "descriptorByName/" + ShiftLeftTestPackBuilder.class.getName()
            + "/fillPackIdItems",
        HttpURLConnection.HTTP_BAD_METHOD);
  }

  @Test
  public void theConfigFormStillFillsThePackIdDropdown() throws Exception {
    // ... and the Jelly side needs no change for it: core's select control issues its fill
    // request as a POST (lib/form/select/select.js), so the dropdown keeps working.
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds-form",
        "ShiftLeft credentials",
        "user@example.com",
        "password"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    FreeStyleProject p = jenkins.createFreeStyleProject();
    ShiftLeftTestPackBuilder b = new ShiftLeftTestPackBuilder("http://localhost:" + port);
    b.setCredentialsId("shiftleft-creds-form");
    b.setPackId("pack_1");
    p.getBuildersList().add(b);
    p.save();

    String fillPath = p.getUrl() + "descriptorByName/"
        + ShiftLeftTestPackBuilder.class.getName() + "/fillPackIdItems";

    // Opening the config page must not produce the interceptor's "POST required" page, which is
    // what select.js would render into the field's error area if the fill went out as a GET.
    JenkinsRule.WebClient wc = jenkins.createWebClient();
    HtmlPage configPage = wc.getPage(p, "configure");
    wc.waitForBackgroundJavaScript(20000);
    assertFalse("the select control's fill request has to be accepted",
        configPage.asXml().contains("POST required"));
    assertNotNull(configPage.getFormByName("config").getSelectByName("_.packId"));

    // And the method answers a POST with the packs the server lists. (On page load the request
    // can go out before the credentials dropdown next to it has filled, in which case it answers
    // with its own "configure Server URL and Credentials first" placeholder - that is the form's
    // own chaining, not the POST requirement, so the parameters are supplied directly here.)
    WebRequest fill = new WebRequest(wc.createCrumbedUrl(fillPath), HttpMethod.POST);
    fill.setRequestParameters(Arrays.asList(
        new NameValuePair("serverUrl", "http://localhost:" + port),
        new NameValuePair("credentialsId", "shiftleft-creds-form")));
    Page filled = wc.getPage(fill);
    assertEquals(200, filled.getWebResponse().getStatusCode());
    assertTrue("expected the stubbed pack in the response: "
            + filled.getWebResponse().getContentAsString(),
        filled.getWebResponse().getContentAsString().contains("pack_1"));
  }

  @Test
  public void theFillMethodsCheckConfigureOnTheJobInScope() throws Exception {
    UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        "shiftleft-creds-scoped",
        "ShiftLeft credentials",
        "user@example.com",
        "password"
    );
    SystemCredentialsProvider.getInstance().getCredentials().add(c);
    SystemCredentialsProvider.getInstance().save();

    jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
    FreeStyleProject p = jenkins.createFreeStyleProject();
    jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
        .grant(Jenkins.READ, Item.READ).everywhere().to("configurer", "reader")
        .grant(Item.CONFIGURE).onItems(p).to("configurer"));

    String fillPath = p.getUrl() + "descriptorByName/"
        + ShiftLeftTestPackBuilder.class.getName() + "/fillCredentialsIdItems";

    // Item.CONFIGURE granted on this job only: checking it against the job in scope passes, where
    // checking it against the Jenkins root did not. The listing is made as the job's own
    // authentication, so the global credential is actually offered.
    Page allowed = jenkins.createWebClient().login("configurer")
        .getPage(new URL(jenkins.getURL(), fillPath));
    assertEquals(200, allowed.getWebResponse().getStatusCode());
    assertTrue("the job's credentials have to be listed: "
            + allowed.getWebResponse().getContentAsString(),
        allowed.getWebResponse().getContentAsString().contains("shiftleft-creds-scoped"));

    // No configure right anywhere: still refused.
    jenkins.createWebClient().login("reader")
        .assertFails(fillPath, HttpURLConnection.HTTP_FORBIDDEN);
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

