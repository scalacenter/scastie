```bash
docker login
```

```bash
docker build -t scalacenter/scastie-docker-sbt:0.0.34 .
docker push scalacenter/scastie-docker-sbt:0.0.34
docker push scalacenter/scastie-docker-sbt:latest
```
