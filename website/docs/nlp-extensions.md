# NLP Extensions

Spring Prism keeps `prism-core` deterministic and zero-dependency. Higher-level person-name
detection lives in the optional `prism-extensions-nlp` module so teams can opt in explicitly when
they want more recall than regex-style detectors provide.

## Design Goals

- keep `prism-core` unchanged and fast
- make person-name redaction explicit, never implicit
- reduce false positives on technical text such as `Spring Boot` or `Redis Cluster`
- support a stronger hybrid mode for enterprise deployments that can provide a local OpenNLP model

## Detection Pipeline

The person-name extension works in three stages:

1. Candidate extraction
   Heuristic matching finds capitalized name-like spans and optional honorifics.
2. Optional OpenNLP candidate extraction
   OpenNLP `NameFinderME` contributes additional person-name candidates when a model is configured.
3. Contextual scoring
   Spring Prism merges candidates, applies blocked technical phrases, and scores the span based on:
   titles, token count, nearby human-oriented context, backend agreement, and known technical terms.

That gives the module a safer profile than raw NER alone:

- `heuristic` keeps rollout simple and conservative
- `hybrid` improves recall when the model and heuristics agree
- `opennlp` is available for teams that want direct model-only behavior

## Configuration

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: classpath:/models/en-ner-person.bin
        confidence-threshold: 4
        max-tokens: 3
        allow-single-token-with-title: true
        positive-context-terms:
          - customer
          - employee
          - patient
        blocked-phrases:
          - Spring Boot
          - Azure OpenAI
          - Redis Cluster
```

If you want a practical guide for where to place the model and how to mount it in real
deployments, continue with [NLP Model Guide](/docs/nlp-model-guide).

## Backend Modes

### `heuristic`

- no model dependency
- easiest path for initial adoption
- best when you want strict rollout control and simple operations

### `opennlp`

- requires `spring.prism.extensions.nlp.model-resource`
- useful when your team already curates OpenNLP models internally
- should still be tested on technical corpora before wide production rollout

### `hybrid`

- requires `spring.prism.extensions.nlp.model-resource`
- combines heuristic and OpenNLP candidates
- recommended enterprise mode when you want stronger recall with better false-positive control

## Production Guidance

- Treat the OpenNLP model as a versioned deployment artifact and roll it out the same way on every
  node.
- Keep `blocked-phrases` tuned for your domain vocabulary, especially product names and platform
  terms.
- Validate on realistic corpora such as support tickets, CRM notes, or RAG chunks before broad
  enablement.
- Start with `heuristic` in conservative environments, then promote to `hybrid` once the model has
  been validated against production-shaped text.
- Keep the extension disabled in services that do not need person-name redaction.
- Use a versioned model artifact path and deploy the exact same file to every node.

## Observability

When the extension is enabled through the starter:

- `NLP_EXTENSIONS` appears in the active rule pack list
- `PERSON_NAME` appears in the entity metrics once matches are detected
- the default deterministic detector set remains unchanged when the extension is disabled

## Test Coverage Expectations

Every change to this module should update:

- unit tests in `prism-extensions-nlp`
- starter wiring tests when autoconfiguration changes
- `prism-integration-tests` for end-to-end opt-in behavior and false-positive guardrails
