# Build stage - cache dependencies
FROM babashka/babashka:1.12.197-alpine AS builder
WORKDIR /app
RUN bb --prepare

COPY src/ ./src/
COPY bb.edn .

RUN bb uberjar coffeebot.jar -m robot-disco.robonona.coffeebot

# Runtime stage
FROM babashka/babashka:1.12.197-alpine
WORKDIR /app

# Copy necessary files
COPY --from=builder /app/coffeebot.jar ./coffeebot.jar

# Chat backend: "slack" (default) or "mattermost"
ENV ROBONONA_CHAT_BACKEND="slack"
# Slack config (required if using slack backend)
ENV ROBONONA_SLACK_TOKEN=""
ENV ROBONONA_SLACK_CHANNEL=""
# Matching algorithm: "random" (default) or "round-robin"
ENV ROBONONA_MATCH_ALGORITHM="random"
# Required for round-robin: path to history file (needs persistent volume)
ENV ROBONONA_MATCH_HISTORY_FILE=""

# Run babashka uberjar
CMD ["bb", "--jar", "./coffeebot.jar"]
