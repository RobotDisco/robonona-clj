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

- **mattermost/protocol.clj** - Mattermost client protocol (mirrors Slack protocol). *Untested, best-effort implementation.*

- **mattermost/http_client.clj** - HTTP implementation of the Mattermost protocol. *Untested, best-effort implementation.*

- **legacy/mattermost.clj** - Legacy Mattermost API client (kept for reference, unused)

### Testing

Tests use `clojure.spec.test.alpha` for property-based testing. A mock client (`test/robot_disco/robonona/slack/mock.clj`) implements the protocol for isolated testing. Integration tests are tagged with `:integration` metadata and excluded from default test runs.

## Environment Variables

All environment variables use the `ROBONONA_` prefix:

- `ROBONONA_CHAT_BACKEND` - Chat service: `slack` (default) or `mattermost`
- `ROBONONA_SLACK_TOKEN` - Slack bot/user token
- `ROBONONA_SLACK_CHANNEL` - Slack channel ID
- `ROBONONA_MATTERMOST_URL` - Mattermost API base URL
- `ROBONONA_MATTERMOST_TOKEN` - Mattermost access token
- `ROBONONA_MATTERMOST_TEAM` - Mattermost team name
- `ROBONONA_MATTERMOST_CHANNEL` - Mattermost channel name
- `ROBONONA_MATCH_ALGORITHM` - `random` (default) or `round-robin`
- `ROBONONA_MATCH_HISTORY_FILE` - Path to history file (required for round-robin)

## Kubernetes Deployment

The Helm chart is in `helm-chart/`. Deploy with:

```bash
# Install (Slack)
helm install coffeebot ./helm-chart \
  --namespace coffeebot --create-namespace \
  --set slack.token="xoxb-your-token" \
  --set slack.channel="C12345678"

# Upgrade
helm upgrade coffeebot ./helm-chart \
  --namespace coffeebot \
  --set slack.token="xoxb-your-token" \
  --set slack.channel="C12345678"
```

## Conventions

- All source files include `SPDX-License-Identifier: EPL-1.0` header
- Commits use conventional format: `feat:`, `fix:`, `docs:`, `build:`, `test:`, `refactor:`

## Development Practices

- **Test-driven development** - Write tests before implementation
- **Functional programming** - Isolate state management; prefer pure functions
- **Clojure spec** - Use specs for data validation and generative testing

## Development Workflow

- **Local development** - Done via the REPL
- **Local testing** - Run tests via Babashka (`bb test`)
- **Deployment** - Assumes Kubernetes (see Helm chart in `helm-chart/`)

## Versioning

This project follows [semantic versioning](https://semver.org/). At the end of each session, ensure versions are incremented appropriately:

- **App version** (`helm-chart/Chart.yaml` `appVersion` and image tag in `templates/cronjob.yaml`): Increment for application code changes
- **Chart version** (`helm-chart/Chart.yaml` `version`): Increment for Helm chart changes only

Version increments:
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, minor changes
