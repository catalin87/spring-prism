# Contributing to Spring Prism

Thank you for your interest in hardening Spring Prism! As a privacy-first firewall, we hold contributions to the highest architectural standards.

## Developer Certificate of Origin (DCO)
We strictly enforce the DCO to ensure all code is legally cleared for inclusion under the **EUPL 1.2** license. 
Every single commit **MUST** be signed off. You can do this by using the `-s` or `--signoff` flag when committing:
```bash
git commit -s -m "feat: added new EU VAT detector"
```
Commits missing the `Signed-off-by` trailer will be automatically rejected by the CI pipeline.

## How to Implement a PrismRulePack
Spring Prism relies on a localized, zero-dependency plugin architecture for locating PII entities. To contribute a new regional bundle:

1. **Implement `PrismRulePack`**: Create a concrete class in `io.catalin87.prism.core` implementing the interface.
2. **Define the Locale**: Implement `getLocale()` to return the regional grouping (e.g., `"EU"`, `"UK"`, `"UNIVERSAL"`).
3. **Register Detectors**: Implement `getDetectors()` and expose your `List<PiiDetector>` implementations.
4. **Build the Detectors**: Ensure your `PiiDetector` implementations accurately compute string offsets and return immutable `PiiCandidate` records.
5. **Fail Open**: Detectors must gracefully handle invalid buffers without throwing runtime exceptions.
6. **Property-Based Testing**: You MUST include `jqwik` property tests for new Regex/NLP detectors to guarantee extreme Unicode handling stability!
