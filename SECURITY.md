# Security Policy

## Supported Versions
Spring Prism currently provides security updates and cryptographic patches for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 1.x     | :white_check_mark: |

## Reporting a Vulnerability

**DO NOT OPEN A PUBLIC GITHUB ISSUE FOR PII LEAKAGE VULNERABILITIES.**

If you discover a scenario where structured PII bypasses a `PrismRulePack`, an exploit allowing HMAC-SHA256 signature reversal, or an issue leading to `String` memory exposure, please report it privately:

1. Email the core maintainer exactly with the subject: `[SECURITY] Spring Prism Leakage Report`.
2. Include a detailed Proof of Concept (PoC) demonstrating the leakage or vault fault.
3. We will acknowledge the report within 48 hours and coordinate a private mitigation patch before publishing a CVE disclosure.

## Verification:
All releases are signed with GPG key 2456988305F327B2.
You can find the public key in the /certs folder of this repository.
