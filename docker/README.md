```bash
docker login
```

```bash
docker build -t scalacenter/scastie-docker-sbt:0.0.31 .
docker push scalacenter/scastie-docker-sbt:0.0.31

docker build -t scalacenter/scastie-docker-sbt:latest .
docker push scalacenter/scastie-docker-sbt:latest
```
