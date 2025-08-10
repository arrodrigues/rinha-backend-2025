# rinha-backend-2025

To launch your tests:

``` bash
mvn clean test
```

To package your application:

``` bash
mvn clean package
```

To run your application:

```bash
java -jar target/*-fat.jar
```

# Endpoints

- Create a payment

```bash
curl --location 'http://localhost:9999/payments' \
--header 'Content-Type: application/json' \
--data '{
    "correlationId": "4a7901b8-7d26-4d9d-aa19-4dc1c7cf60b3",
    "amount": 19.90
}'
```

- Get payment summary

```bash
curl --location 'http://localhost:9999/payments-summary'
```

# Execute All Projects And K6 Tests

- Build the Docker image

```bash
docker build -f Dockerfile-v2 -t rinha-backend-payment-gateway .
```

- Install k6

```bash
brew install k6
```

- Start the payment-processor service (
  repo:[rinha-de-backend-2025](https://github.com/zanfranceschi/rinha-de-backend-2025/))

```bash
cd payment-processor
docker-compose -f docker-compose-arm64.yml up
```

- Start the backend service (this repo)

```bash
docker-compose up
```

- Run the tests (
  repo:[rinha-de-backend-2025](https://github.com/zanfranceschi/rinha-de-backend-2025/))

```bash
cd rinha-test
k6 run rinha.js
```

- Check HA proxy statistics
  http://localhost:8404/stats

