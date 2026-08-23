package io.jenkins.plugins.shiftleft;

import hudson.model.Result;

final class QualityGateDecision {
  final String decision;
  final Result jenkinsResult;

  QualityGateDecision(String decision, Result jenkinsResult) {
    this.decision = decision;
    this.jenkinsResult = jenkinsResult;
  }
}

