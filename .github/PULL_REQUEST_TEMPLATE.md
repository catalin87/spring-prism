## Description
<!-- Describe your implementation, architectural decisions, and specific RulePack changes here. -->

## Related Issues
<!-- Link to the GitHub issue this PR resolves (e.g., Fixes #123) -->

## Quality Guardrails Checklist
As the Lead Architect mandates, please verify the following critical constraints are met before requesting a Code Review:

- [ ] **License headers present:** The EUPL 1.2 "Catalin Dordea" copyright header has been embedded on ALL new `.java` files.
- [ ] **Jqwik tests added:** Comprehensive property-based edge-case tests are included for new Regex or parsing boundaries.
- [ ] **Zero-dependency check passed:** Absolutely NO external libraries were integrated into the `prism-core` engine.
- [ ] **Virtual Thread Safe:** No `synchronized` blocks or `ThreadLocal` allocations were introduced.
- [ ] **DCO Signed:** All commits in this branch contain the `Signed-off-by` flag.
