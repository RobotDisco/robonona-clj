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

# Required environment variables
ENV SLACK_TOKEN=""
ENV SLACK_CHANNEL=""g

# Run babashka uberjar
CMD ["bb", "--jar", "./coffeebot.jar"]
