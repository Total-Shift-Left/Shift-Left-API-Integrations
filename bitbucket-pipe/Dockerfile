# Shift-Left Studio Bitbucket pipe.
#
# Why FROM totalshiftleft/ci-runner:1 rather than node:20-alpine + `npm i @totalshiftleft/ci`:
# it is strictly simpler and strictly more reproducible. The runner image already contains the
# exact same source, pinned to the same major, with the entrypoint wired up; rebuilding from npm
# would add a network install to every image build and let the pipe drift from the runner used by
# the other CI integrations. The pipe therefore adds packaging metadata and one behavioural fix,
# and nothing else.
FROM totalshiftleft/ci-runner:1

# The base image drops to the unprivileged `node` user, which is right for a bare `docker run`
# with a bind mount. Bitbucket Pipelines clones into /opt/atlassian/pipelines/agent/build owned
# by root and runs pipes as root, so an unprivileged user cannot write the JSON/JUnit artifacts
# there. Revert to root for this image only.
USER root

LABEL org.opencontainers.image.title="Shift-Left Studio Test Pack" \
      org.opencontainers.image.description="Run a Shift-Left Studio test run pack and gate the build on its pass rate." \
      org.opencontainers.image.vendor="Total Shift Left" \
      org.opencontainers.image.url="https://totalshiftleft.ai" \
      org.opencontainers.image.licenses="MIT"

# ENTRYPOINT is inherited from the base image:
#   ["node", "/opt/shiftleft/bin/shiftleft-ci.js"]
# Configuration is entirely through the SHIFTLEFT_* variables declared in pipe.yml.
