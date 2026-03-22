# Supported Detectors

Spring Prism provides a robust suite of built-in PII detectors grouped into `PrismRulePack` configurations for easy adoption.

## Universal Detectors
The `UniversalRulePack` contains detectors applicable in nearly any context.

| Entity Type | Description | Validation |
|---|---|---|
| `EMAIL` | Standard email addresses (RFC 5321). | Backtracking-safe regex. |
| `CREDIT_CARD` | Major credit cards (Visa, MC, Amex, etc.). | Luhn algorithm Checksum. |
| `SSN` | US Social Security Numbers. | Invalid area code (000, 666, 9xx) exclusions. |
| `PHONE_NUMBER` | Common international and North American phone numbers. | Normalized digit-count and false-positive filtering. |
| `IP_ADDRESS` | IPv4 and IPv6 addresses. | Octet range (0-255) and format validation. |

## European Detectors
The `EuropeRulePack` extends the Universal set with GDPR-critical European PII.

| Entity Type | Description | Validation |
|---|---|---|
| `IBAN` | International Bank Account Numbers. | ISO 13616 Modulo-97 checksum. |
| `EU_VAT` | EU Member State VAT numbers. | Format validation per EC VIES. |
| `PESEL` | Polish Personal ID Number. | GUS weighted checksum. |
| `CNP` | Romanian Personal Numerical Code. | ANP weighted Mod-11 checksum. |
| `NINO` | UK National Insurance Numbers. | HMRC prefix/suffix logic exclusions. |

## Custom Detectors

You can implement your own detectors by extending the `PiiDetector` interface.

```java
public class MyDetector implements PiiDetector {
  @Override
  public @NonNull String getEntityType() {
    return "MY_ENTITY_TYPE";
  }

  @Override
  public @NonNull List<PiiCandidate> detect(@NonNull String input) {
    // Return a list of detected candidates with their positions...
  }
}
```

Then add them to a custom `PrismRulePack`:

```java
public class MyRulePack implements PrismRulePack {
  @Override
  public @NonNull String getName() {
    return "MY_CUSTOM_PACK";
  }

  @Override
  public @NonNull List<PiiDetector> getDetectors() {
    return List.of(new MyDetector());
  }
}
```

## Optional Integration Detectors

`prism-core` stays regex and checksum focused by design. If you need probabilistic or model-backed detection such as person names, add it in `prism-spring-ai` or `prism-spring-boot-starter` behind optional classpath checks.

This split keeps the core:

- dependency-free
- deterministic and fast to test
- usable outside Spring

It also keeps fail-open metrics and strict-mode policy in the integration layer where Micrometer and application configuration already exist.
