package io.jenkins.plugins.shiftleft;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jenkins.model.RunAction2;

/**
 * Build page action: summary UI and sidebar link. Uses Jenkins symbol icons (see design library).
 */
public class ShiftLeftTestPackAction implements RunAction2 {
  private static final long serialVersionUID = 1L;

  private final ShiftLeftRunSummary summary;
  private transient @CheckForNull Run<?, ?> run;

  public ShiftLeftTestPackAction(@NonNull ShiftLeftRunSummary summary) {
    this.summary = summary;
  }

  @Override
  public void onAttached(Run<?, ?> run) {
    this.run = run;
  }

  @Override
  public void onLoad(Run<?, ?> run) {
    this.run = run;
  }

  public @CheckForNull Run<?, ?> getRun() {
    return run;
  }

  public ShiftLeftRunSummary getSummary() {
    return summary;
  }

  /**
   * Full URL to browse the JSON summary in the job workspace (Freestyle / classic {@code ws/} is job-scoped, not per-build).
   */
  public @CheckForNull String getJsonSummaryBrowseUrl() {
    return workspaceBrowseUrl(summary.getJsonSummaryRelativePath());
  }

  /**
   * Full URL to browse the JUnit XML file in the job workspace.
   */
  public @CheckForNull String getJunitXmlBrowseUrl() {
    return workspaceBrowseUrl(summary.getJunitXmlRelativePath());
  }

  private @CheckForNull String workspaceBrowseUrl(@CheckForNull String workspaceRelativePath) {
    if (workspaceRelativePath == null || run == null) {
      return null;
    }
    String encoded = encodeWorkspaceRelativePathForUrl(workspaceRelativePath);
    if (encoded.isEmpty()) {
      return null;
    }
    return run.getParent().getAbsoluteUrl() + "ws/" + encoded;
  }

  /**
   * Normalize slashes and percent-encode each path segment for use after {@code .../ws/}.
   */
  static String encodeWorkspaceRelativePathForUrl(String relative) {
    String norm = relative.replace('\\', '/').replaceFirst("^/+", "").trim();
    if (norm.isEmpty()) {
      return "";
    }
    String[] parts = norm.split("/");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('/');
      }
      sb.append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
    }
    return sb.toString();
  }

  /**
   * Symbol ids for modern Jenkins (see /design-library/Symbols); avoids removed /images/24x24 paths.
   */
  @Override
  public String getIconFileName() {
    if (summary == null) {
      return "symbol-document-text";
    }

    String decision = summary.getDecision();
    if (decision != null) {
      String decisionLower = decision.toLowerCase();
      if (decisionLower.equals("passed")) {
        return "symbol-status-blue";
      } else if (decisionLower.equals("failed")) {
        return "symbol-status-red";
      } else if (decisionLower.equals("timeout")) {
        return "symbol-warning";
      }
    }

    String status =
        summary.getLatestStatus() != null ? summary.getLatestStatus() : summary.getTriggerStatus();
    if (status != null) {
      String statusLower = status.toLowerCase();
      if (statusLower.equals("completed") || statusLower.equals("passed")) {
        return "symbol-status-blue";
      } else if (statusLower.equals("failed") || statusLower.equals("error")) {
        return "symbol-status-red";
      } else if (statusLower.equals("running") || statusLower.equals("pending")) {
        return "symbol-hourglass";
      } else if (statusLower.equals("timeout")) {
        return "symbol-warning";
      }
    }

    return "symbol-document-text";
  }

  @Override
  public String getDisplayName() {
    return "Shift-Left API Automation Integration";
  }

  @Override
  public String getUrlName() {
    return "shiftleft-test-pack";
  }
}
