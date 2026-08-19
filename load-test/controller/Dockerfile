FROM grafana/k6:0.57.0 AS k6
FROM node:22-alpine
COPY --from=k6 /usr/bin/k6 /usr/bin/k6
WORKDIR /app
COPY load-test/controller/package.json load-test/controller/server.js ./
COPY load-test/async-checkout-load.js /scripts/async-checkout-load.js
EXPOSE 6565
CMD ["npm", "start"]
