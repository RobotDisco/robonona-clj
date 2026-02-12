# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Robonona is a Clojure application for workplace chat integrations, primarily "coffeebot" which randomly pairs employees for coffee chats. It supports both Slack (current focus) and Mattermost (legacy).

## Common Commands

```bash
bb test              # Run unit tests (excludes integration tests)
bb test:full         # Run all tests including integration tests
bb lint              # Run clj-kondo linter
bb format            # Autoformat code with cljfmt
```

Run the coffeebot directly:
```bash
bb src/robot_disco/robonona/coffeebot.clj
```

## Architecture

### Core Components

- **matcher.clj** - Random pairing algorithm (`random-match`). Shuffles users and creates pairs; handles odd-numbered groups by returning an unmatched user. Uses `clojure.spec` for data validation with user-specific specs.

- **slack/protocol.clj** - Defines the `Client` protocol with three methods:
  - `get-channel-users` - Fetch user IDs from a channel
  - `get-conversation-id` - Create/get DM conversation between users
  - `post-message` - Send messages to channels

- **slack/http_client.clj** - HTTP implementation of the Client protocol using Babashka's HTTP client

- **coffeebot.clj** - Main application logic that orchestrates matching and messaging. Also serves as the Babashka script entry point.

- **mattermost.clj** - Legacy Mattermost API client with pagination handling

### Testing

Tests use `clojure.spec.test.alpha` for property-based testing. A mock client (`test/robot_disco/robonona/slack/mock.clj`) implements the protocol for isolated testing. Integration tests are tagged with `:integration` metadata and excluded from default test runs.

## Environment Variables

For Slack integration:
- `SLACK_TOKEN` - Bot/user token for Slack API
- `SLACK_CHANNEL` - Channel ID to fetch users from

## Kubernetes Deployment

The Helm chart is in `helm-chart/`. Deploy with:

```bash
# Install
helm install coffeebot ./helm-chart \
  --namespace coffeebot --create-namespace \
  --set slackToken="xoxb-your-token" \
  --set slackChannel="C12345678"

# Upgrade
helm upgrade coffeebot ./helm-chart \
  --namespace coffeebot \
  --set slackToken="xoxb-your-token" \
  --set slackChannel="C12345678"
```

## Conventions

- All source files include `SPDX-License-Identifier: EPL-1.0` header
- Commits use conventional format: `feat:`, `fix:`, `docs:`, `build:`, `test:`, `refactor:`

## Versioning

This project follows [semantic versioning](https://semver.org/). At the end of each session, ensure versions are incremented appropriately:

- **App version** (`helm-chart/Chart.yaml` `appVersion` and image tag in `templates/cronjob.yaml`): Increment for application code changes
- **Chart version** (`helm-chart/Chart.yaml` `version`): Increment for Helm chart changes only

Version increments:
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, minor changes
