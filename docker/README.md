```bash
docker login
```

```bash
docker build -t scalacenter/scastie-docker-sbt:0.0.30 .
docker push scalacenter/scastie-docker-sbt:0.0.30

docker build -t scalacenter/scastie-docker-sbt:latest .
docker push scalacenter/scastie-docker-sbt:latest

```
