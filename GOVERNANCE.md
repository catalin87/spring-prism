# Spring Prism Governance Model

This document outlines the governance model for the Spring Prism project. Its purpose is to clarify how decisions are made, how the project is maintained, and the responsibilities of different roles within our community.

Given that Spring Prism operates in the highly sensitive domains of data privacy, compliance (EU AI Act, GDPR), and enterprise security, our governance model prioritizes **architectural integrity, security, and long-term stability** over rapid, uncoordinated changes.

## 1. Project Roles

### 🧑‍💻 Users
Users are community members who have a need for the project. They are the most important members of the community and without them, the project would have no purpose. Anyone can be a User. Users are encouraged to participate by providing feedback, requesting features, and reporting bugs.

### 🛠️ Contributors
Contributors are Users who contribute to the project in concrete ways. This can include code, documentation, issuing bug reports, or helping other users. 
* All code Contributors **must** sign the project's Contributor License Agreement (CLA) before their Pull Requests can be merged. This ensures the legal safety of the codebase for all enterprise and open-source users.

### 🛡️ Maintainers
Maintainers are trusted Contributors who have demonstrated a strong commitment to the project, its architectural vision, and its security standards. 
* Maintainers have write access to the repository.
* They can label issues, review Pull Requests, and merge non-breaking changes.
* They are responsible for helping triage bugs and guiding new Contributors.
* *Becoming a Maintainer:* This role is by invitation only from the Project Lead, based on a history of high-quality contributions and alignment with the project's goals.

### 👑 Project Lead (Founder)
The Spring Prism project was founded and is currently led by **Catalin Dordea**. The Project Lead acts as the ultimate decision-maker (often referred to in open-source as the *Benevolent Dictator For Life* or BDFL).
* **Responsibilities:** The Lead manages the overall project vision, release cycles, dual-licensing strategy, and final architectural decisions (such as maintaining the strict zero-dependency rule in `prism-core`).
* **Veto Power:** The Lead has the final say on all merges, feature additions, and roadmap prioritization to ensure the project remains enterprise-ready and legally compliant.

## 2. Decision Making Process

We strive to operate through consensus. When a new feature or architectural change is proposed (via a GitHub Issue or PR), we encourage open discussion among Users, Contributors, and Maintainers.

However, in cases where consensus cannot be reached, or if a proposed change conflicts with the core philosophy of the project (e.g., adding unnecessary bloat, compromising the deterministic nature of the PII detectors, or violating the zero-dependency core policy), the **Project Lead holds the final veto**. 

This "buck stops here" approach ensures that Spring Prism remains a reliable, focused, and secure tool for enterprise environments.

## 3. Intellectual Property and Licensing Strategy

Spring Prism is distributed under the **EUPL 1.2** license for the open-source community. 

To support the sustainable development of the project and cater to corporate environments that cannot adopt copyleft licenses, the project operates on a **Dual Licensing** model. 
* By signing the required CLA, Contributors grant the Project Lead the right to sub-license their contributions under commercial terms to enterprise clients. 
* This governance structure ensures that the Project Lead can legally provide Commercial Licenses and Enterprise SLA/Support without IP fragmentation.

## 4. Code of Conduct

All members of the Spring Prism community, regardless of their role, are expected to adhere to our [Code of Conduct](./CODE_OF_CONDUCT.md). We are committed to providing a welcoming and inspiring environment for all.
