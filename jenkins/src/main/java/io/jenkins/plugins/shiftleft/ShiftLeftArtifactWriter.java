package io.jenkins.plugins.shiftleft;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ShiftLeftArtifactWriter {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ShiftLeftArtifactWriter() {}

  static void writeJsonSummary(
      FilePath workspace,
      String relativePath,
      ShiftLeftRunSummary summary,
      TaskListener listener
  ) throws IOException, InterruptedException {
    Objects.requireNonNull(workspace, "workspace");
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(listener, "listener");

    FilePath out = workspace.child(relativePath);
    String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
    try (OutputStream os = out.write()) {
      os.write(json.getBytes(StandardCharsets.UTF_8));
    }
    listener.getLogger().println("[ShiftLeft] Wrote JSON summary: " + out.getRemote());
  }

  static void writeJUnitXml(
      FilePath workspace,
      String relativePath,
      ShiftLeftRunSummary summary,
      ShiftLeftApiClient.ResultsResponse results,
      TaskListener listener
  ) throws IOException, InterruptedException {
    Objects.requireNonNull(workspace, "workspace");
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(results, "results");
    Objects.requireNonNull(listener, "listener");

    String suiteName = "ShiftLeft Test Pack " + nullToEmpty(summary.getPackId());
    String timestamp = Instant.now().toString();

    int tests = Math.max(summary.getTotal(), results.resultsCount);
    int failures = Math.max(0, summary.getFailed());
    int errors = Math.max(0, summary.getError());

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<testsuite")
        .append(" name=\"").append(xmlEscape(suiteName)).append("\"")
        .append(" tests=\"").append(tests).append("\"")
        .append(" failures=\"").append(failures).append("\"")
        .append(" errors=\"").append(errors).append("\"")
        .append(" timestamp=\"").append(xmlEscape(timestamp)).append("\"")
        .append(">\n");

    xml.append("  <properties>\n");
    xml.append("    <property name=\"serverUrl\" value=\"").append(xmlEscape(nullToEmpty(summary.getServerUrl()))).append("\"/>\n");
    xml.append("    <property name=\"tenantId\" value=\"").append(xmlEscape(nullToEmpty(summary.getTenantId()))).append("\"/>\n");
    xml.append("    <property name=\"packId\" value=\"").append(xmlEscape(nullToEmpty(summary.getPackId()))).append("\"/>\n");
    xml.append("    <property name=\"executionId\" value=\"").append(xmlEscape(nullToEmpty(summary.getExecutionId()))).append("\"/>\n");
    xml.append("    <property name=\"resultsPackName\" value=\"").append(xmlEscape(nullToEmpty(results.packName))).append("\"/>\n");
    xml.append("    <property name=\"resultsPackId\" value=\"").append(xmlEscape(nullToEmpty(results.packId))).append("\"/>\n");
    xml.append("    <property name=\"resultsExecutionId\" value=\"").append(xmlEscape(nullToEmpty(results.executionId))).append("\"/>\n");
    xml.append("    <property name=\"resultsStartTime\" value=\"").append(xmlEscape(nullToEmpty(results.startTimeIso))).append("\"/>\n");
    xml.append("    <property name=\"resultsEndTime\" value=\"").append(xmlEscape(nullToEmpty(results.endTimeIso))).append("\"/>\n");
    xml.append("  </properties>\n");

    for (ShiftLeftApiClient.ResultItem item : results.results) {
      String testName = item.testName != null ? item.testName : nullToEmpty(item.testId);
      String status = item.status == null ? "" : item.status.trim().toUpperCase();
      double seconds = toSeconds(item.duration);

      xml.append("  <testcase")
          .append(" classname=\"ShiftLeft\"")
          .append(" name=\"").append(xmlEscape(testName)).append("\"")
          .append(" time=\"").append(String.format(java.util.Locale.ROOT, "%.3f", seconds)).append("\"")
          .append(">\n");

      if ("FAILED".equals(status)) {
        xml.append("    <failure message=\"").append(xmlEscape(truncate(nullToEmpty(item.error), 500))).append("\">")
            .append(xmlEscape(truncate(nullToEmpty(item.error), 2000)))
            .append("</failure>\n");
      } else if ("ERROR".equals(status)) {
        xml.append("    <error message=\"").append(xmlEscape(truncate(nullToEmpty(item.error), 500))).append("\">")
            .append(xmlEscape(truncate(nullToEmpty(item.error), 2000)))
            .append("</error>\n");
      }

      String shiftleftMeta = "shiftleft.responseTimeMs=" + item.responseTime
          + " shiftleft.startTime=" + nullToEmpty(item.startTimeIso)
          + " shiftleft.endTime=" + nullToEmpty(item.endTimeIso);
      xml.append("    <system-out>").append(xmlEscape(shiftleftMeta)).append("</system-out>\n");

      xml.append("  </testcase>\n");
    }

    xml.append("</testsuite>\n");

    FilePath out = workspace.child(relativePath);
    try (OutputStream os = out.write()) {
      os.write(xml.toString().getBytes(StandardCharsets.UTF_8));
    }
    listener.getLogger().println("[ShiftLeft] Wrote JUnit XML: " + out.getRemote());
  }

  private static String nullToEmpty(@CheckForNull String s) {
    return s == null ? "" : s;
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }

  private static double toSeconds(long duration) {
    if (duration <= 0) return 0.0;
    // Heuristic: if value looks like ms, convert to seconds.
    if (duration > 1000L) {
      return duration / 1000.0;
    }
    return (double) duration;
  }

  private static String xmlEscape(String s) {
    return s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}

