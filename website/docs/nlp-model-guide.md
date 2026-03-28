---
sidebar_position: 7
---

# NLP Model Guide

Spring Prism supports person-name detection through the optional `prism-extensions-nlp` module.
If you choose `backend=opennlp` or `backend=hybrid`, you must provide a readable OpenNLP
`TokenNameFinderModel`.

This guide shows the practical deployment path.

## When you need this

You need a model only when:

- `spring.prism.extensions.nlp.enabled=true`
- and `spring.prism.extensions.nlp.backend=opennlp` or `hybrid`

You do **not** need a model for:

- `backend=heuristic`

That makes `heuristic` the best first step when a team wants zero-friction rollout.

## Recommended model artifact

For English person names, a common starting point is the OpenNLP person name finder model:

- file name: `en-ner-person.bin`

Treat the model as an internal deployment artifact, not as an ad-hoc local file copied around by
hand.

## Quick download helper

Spring Prism now ships small helper scripts that download the recommended OpenNLP model into the
benchmark-friendly local path:

- Windows CMD:

```bat
scripts\download-nlp-model.cmd
```

- Unix-like shells:

```bash
./scripts/download-nlp-model.sh
```

Default target path:

```text
prism-benchmarks/models/en-ner-person.bin
```

You can also pass a custom folder path as the first argument to either script.

## Supported resource styles

Spring Prism reads the model through `spring.prism.extensions.nlp.model-resource`, so the model can
be delivered as:

- `classpath:/models/en-ner-person.bin`
- `file:/opt/spring-prism/models/en-ner-person.bin`
- another Spring `Resource` location that is readable at startup

## Fastest local-development path

Place the model in your application resources:

```text
src/main/resources/models/en-ner-person.bin
```

Then configure:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: classpath:/models/en-ner-person.bin
```

This is the easiest path for local development, demos, and small controlled deployments.

## Production deployment path

For multi-node or containerized deployments, prefer an external file path:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: file:${PRISM_NLP_MODEL}
```

Example environment variable:

```bash
export PRISM_NLP_MODEL=/opt/spring-prism/models/en-ner-person.bin
```

This is the recommended pattern because it lets you:

- version the model explicitly
- roll out the same artifact on every node
- update the model independently from application code
- avoid accidental classpath drift across environments

## Container example

Mount the model into the container and point Spring Prism at the mounted file:

```yaml
services:
  app:
    image: your-app:latest
    environment:
      PRISM_NLP_MODEL: /opt/spring-prism/models/en-ner-person.bin
    volumes:
      - ./models/en-ner-person.bin:/opt/spring-prism/models/en-ner-person.bin:ro
```

Spring configuration:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: file:${PRISM_NLP_MODEL}
```

## Validation checklist

Before enabling `opennlp` or `hybrid` broadly, verify:

- every node receives the same model artifact
- the configured `model-resource` is readable at startup
- the application starts cleanly with no NLP model validation error
- `NLP_EXTENSIONS` appears in the active rule pack list
- `PERSON_NAME` appears in metrics after a representative test
- domain-specific blocked phrases are tuned for your platform and product names

## Benchmark path

If you want to run the NLP benchmarks with a real model, keep the downloaded file at the default
location or point the benchmark suite at it explicitly:

```bash
mvn -pl prism-benchmarks -am package -DskipTests
java -Dprism.bench.nlpModel=prism-benchmarks/models/en-ner-person.bin \
  -jar prism-benchmarks/target/benchmarks.jar NlpBenchmark
```

To inspect allocation pressure and GC behavior during repeated scans:

```bash
java -Dprism.bench.nlpModel=prism-benchmarks/models/en-ner-person.bin \
  -jar prism-benchmarks/target/benchmarks.jar NlpBenchmark -prof gc
```

This is useful for validating:

- cold OpenNLP model load cost
- warm repeated OpenNLP detection
- warm repeated hybrid detection
- allocation and GC pressure caused by scan-time temporary objects

## Failure behavior

Spring Prism intentionally fails fast on startup when:

- `backend=opennlp` or `backend=hybrid`
- and the configured model resource is missing or unreadable

This is by design. It prevents a deployment from silently running with a weaker NLP posture than
the team intended.

## Operational advice

- Start with `heuristic` if you want the lowest-risk rollout.
- Promote to `hybrid` after validating realistic text such as support tickets, CRM notes, or RAG
  chunks.
- Keep the model versioned and deployed the same way on every node.
- Treat blocked phrases and confidence thresholds as part of your normal production tuning.

## Related pages

- [NLP Extensions](/docs/nlp-extensions)
- [Configuration](/docs/configuration)
- [Troubleshooting](/docs/troubleshooting)
- [Enterprise Lab](/docs/demo-app)
