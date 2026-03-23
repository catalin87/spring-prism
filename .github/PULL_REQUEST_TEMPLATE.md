## 📝 Description
## 🔗 Related Issues
## 🛡️ Lead Architect Quality Guardrails
*Please verify the following critical constraints are met before requesting a Code Review:*

### ⚙️ Technical Constraints
- [ ] **Zero-dependency check passed:** Absolutely NO external libraries were integrated into the `prism-core` engine.
- [ ] **Virtual Thread Safe:** No `synchronized` blocks or `ThreadLocal` allocations were introduced; pinned threads avoided.
- [ ] **Jqwik tests added:** Comprehensive property-based edge-case tests are included for new Regex or parsing boundaries.
- [ ] **Performance:** No significant latency introduced in the interception pipeline.

### ⚖️ Legal & Compliance
- [ ] **License headers present:** The EUPL 1.2 "Catalin Dordea" copyright header has been embedded on ALL new `.java` files.
- [ ] **CLA Signed:** I have signed the [Spring Prism ICLA](https://gist.github.com/catalin87/URL_GIST_AICI) via the CLA Assistant bot.
- [ ] **Dual-License Agreement:** I acknowledge and agree that this contribution is governed by the project's **Dual Licensing** model (EUPL 1.2 and Commercial Enterprise License).
- [ ] **DCO Signed:** All commits in this branch contain the `Signed-off-by` flag (DCO compliance).

---

## 🧪 Testing Results
- **Detected Patterns:** (e.g., Validated with US SSN, EU IBAN)
- **Ignored Patterns (False Positives):** (e.g., Verified that random 9-digit numbers are NOT redacted)
