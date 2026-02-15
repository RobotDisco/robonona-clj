# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Robonona is a Clojure application for workplace chat integrations, primarily "coffeebot" which randomly pairs employees for coffee chats. It supports both Slack (current focus) and Mattermost (legacy).

## Common Commands

```bash
bb test              # Run unit tests (excludes integration tests)
bb test:full         # DO NOT RUN - makes real API calls; user runs manually
bb lint              # Run clj-kondo linter
bb format            # Autoformat code with cljfmt
bb robonona-version  # Show current version from VERSION file
```

Run the coffeebot directly:
```bash
bb src/robot_disco/robonona/coffeebot.clj
```

### Docker/Kubernetes Commands

```bash
# Build locally (no env vars needed)
bb docker:build

# Push to registry (requires ROBONONA_IMAGE_REPO)
ROBONONA_IMAGE_REPO=ghcr.io/myorg bb docker:push

# Build and push
ROBONONA_IMAGE_REPO=ghcr.io/myorg bb docker:build-push

# Render Helm templates locally (dry-run)
ROBONONA_IMAGE_REPO=ghcr.io/myorg bb helm:template

# Deploy to Kubernetes
ROBONONA_IMAGE_REPO=ghcr.io/myorg bb helm:deploy

# Deploy with additional values file (for secrets)
ROBONONA_IMAGE_REPO=ghcr.io/myorg ROBONONA_HELM_VALUES_FILE=secrets.yaml bb helm:deploy

# Full workflow: build, push, deploy
ROBONONA_IMAGE_REPO=ghcr.io/myorg bb deploy

# Check release status / uninstall
bb helm:status
bb helm:uninstall
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

### Deployment Environment Variables

Used by `bb docker:push`, `bb helm:deploy`, etc.:

- `ROBONONA_IMAGE_REPO` - Docker registry path (e.g., `ghcr.io/myorg`, `docker.io/user`)
- `ROBONONA_IMAGE_NAME` - Image name (default: `coffeebot`)
- `ROBONONA_K8S_NAMESPACE` - Kubernetes namespace (default: `coffeebot`)
- `ROBONONA_HELM_RELEASE` - Helm release name (default: `coffeebot`)
- `ROBONONA_HELM_VALUES_FILE` - Path to additional Helm values file (for secrets)

## Kubernetes Deployment

The Helm chart is in `helm-chart/`. Deploy using bb tasks:

```bash
# Create a values.yaml with secrets (slack.token, slack.channel, etc.)
# Then deploy:
ROBONONA_IMAGE_REPO=ghcr.io/myorg ROBONONA_HELM_VALUES_FILE=values.yaml bb deploy
```

See `helm-chart/values.yaml` for all available configuration options.

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

This project follows [semantic versioning](https://semver.org/). The canonical version is in the `VERSION` file at the repo root.

At the end of each session, ensure versions are incremented appropriately:

- **App version** (`VERSION` file): Increment for application code changes. Keep `helm-chart/Chart.yaml` `appVersion` in sync.
- **Chart version** (`helm-chart/Chart.yaml` `version`): Increment for Helm chart changes only.

Version increments:
- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, minor changes
