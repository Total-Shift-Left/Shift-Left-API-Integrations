# totalshiftleft/ci-runner — the generic CI runner.
#
# One image for every CI system that has no first-party integration: Bamboo, TeamCity,
# AWS CodeBuild, Buildkite, Drone, Harness, Tekton/Argo, and anything else that can run a
# container. It is also what the Bitbucket pipe wraps.
#
# Configure entirely through SHIFTLEFT_* environment variables (see README.md):
#   docker run --rm \
#     -e SHIFTLEFT_SERVER_URL=https://tenant.totalshiftleft.ai \
#     -e SHIFTLEFT_API_EMAIL=ci@example.com \
#     -e SHIFTLEFT_API_PASSWORD=... \
#     -e SHIFTLEFT_PACK_ID=pack_123 \
#     -v "$PWD:/workspace" -w /workspace \
#     totalshiftleft/ci-runner:1
FROM node:20-alpine

WORKDIR /opt/shiftleft

COPY package.json ./
COPY src ./src
COPY bin ./bin

RUN chmod +x bin/shiftleft-ci.js

# Artifacts are written relative to the working directory, so run with the repo bind-mounted
# there. Running as a non-root user keeps those files writable by the CI agent on most systems.
USER node

ENTRYPOINT ["node", "/opt/shiftleft/bin/shiftleft-ci.js"]
