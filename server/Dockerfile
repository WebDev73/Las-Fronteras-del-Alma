# Build from the repository root: docker build -f server/Dockerfile -t fronteras-server .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :server:installDist -Pfronteras.android=false --no-daemon

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 fronteras
COPY --from=build /src/server/build/install/server /opt/fronteras
ENV FRONTERAS_DATA_DIR=/var/lib/fronteras
RUN mkdir -p /var/lib/fronteras && chown fronteras /var/lib/fronteras
USER fronteras
EXPOSE 8080
ENTRYPOINT ["/opt/fronteras/bin/server"]
