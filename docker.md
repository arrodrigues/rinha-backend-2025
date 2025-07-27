# Docker

## Build the Docker image

- V1 - Just copy the jar file from folder `target/` to the Docker image:

```bash
docker build -f Dockerfile-v1 -t rinha-backend-payment-gateway .
```

- V2 - Execute maven and copy the jar file to the Docker image:

```bash
docker build -f Dockerfile-v2 -t rinha-backend-payment-gateway .
```
