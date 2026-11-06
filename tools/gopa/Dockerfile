FROM golang:1.27.3-alpine3.24.4 AS build
WORKDIR /src
COPY go.mod main.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags='-s -w' -o /gopa .
FROM alpine:3.24.4
RUN apk add --no-cache ca-certificates && adduser -D -H -u 10001 gopa && mkdir -p /data && chown 10001:10001 /data
COPY --from=build /gopa /usr/local/bin/gopa
VOLUME ["/data"]
USER 10001
EXPOSE 8443
ENTRYPOINT ["/usr/local/bin/gopa"]
