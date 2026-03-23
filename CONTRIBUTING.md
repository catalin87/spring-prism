# Contributing to Spring Prism

First off, thank you for considering contributing to Spring Prism! It's people like you that make open-source such a powerful tool for building secure and compliant software.

Whether you're fixing a bug, adding a new PII detector, or improving the documentation, your help is deeply appreciated.

Please take a moment to review this document in order to make the contribution process easy and effective for everyone involved.

## ⚖️ Contributor License Agreement (CLA)

**Important:** Before we can accept any code contributions (Pull Requests), you must sign our Contributor License Agreement (CLA).

Because Spring Prism is a security and compliance-focused tool designed for enterprise use, we need to ensure the intellectual property rights of the project are clear. This protects both you (the contributor) and the users of the project.

**Don't worry, it's painless!**
You do not need to print or sign any physical documents. When you open your first Pull Request, a bot ([CLA Assistant](https://cla-assistant.io/)) will automatically comment on it with a link. Simply click the link, sign in with your GitHub account, and click **"I Agree"**.

By signing the CLA, you grant us the right to distribute your code under the project's licenses (including potential future commercial licenses), while you retain full ownership and the right to use your own code elsewhere.

## 🛠️ How to Contribute

### 1. Find an Issue or Propose a Feature
* **Check existing issues:** Look through the [GitHub Issues](https://github.com/catalin87/spring-prism/issues) to see if someone is already working on what you want to do. Issues labeled `good first issue` are a great place to start.
* **Propose something new:** If you want to add a major feature (like a new `PrismVault` implementation or complex `PrismRulePack`), please open an issue first to discuss the design before writing code. This saves everyone time!

### 2. Fork and Branch
1. Fork the repository to your own GitHub account.
2. Clone your fork locally: `git clone https://github.com/your-username/spring-prism.git`
3. Create a new branch for your feature or bugfix: `git checkout -b feature/your-feature-name` (or `bugfix/issue-number`).

### 3. Write Code and Tests
Spring Prism is built on Java 21 and relies heavily on performance and accuracy.
* **Code Style:** Try to match the existing code style. We use standard Java conventions.
* **Testing:** Any new feature, especially new PII Detectors (Regex rules), **must** include unit tests. Please ensure you test for both *True Positives* (finding the PII) and *False Positives* (not accidentally redacting safe text).
* **Documentation:** If you add a new feature, please update the relevant documentation or README.

### 4. Commit and Push
* Make atomic commits with clear, descriptive commit messages.
* Push your branch to your fork: `git push origin feature/your-feature-name`

### 5. Open a Pull Request (PR)
* Open a PR against the `main` branch of the `catalin87/spring-prism` repository.
* Fill out the PR template (if one exists) and describe your changes clearly.
* **Sign the CLA:** Look for the comment from the CLA bot and follow the link to sign it.
* Await review! A maintainer will review your code, run the CI checks, and might request some changes.

## 🐞 Reporting Bugs
If you find a bug, please open an issue and include:
* The version of Spring Prism you are using.
* The framework (Spring AI or LangChain4j) and version.
* A clear description of the problem.
* Steps to reproduce the bug (a minimal code snippet or sample repo is highly appreciated).

## 💬 Community & Code of Conduct
By participating in this project, you agree to abide by our [Code of Conduct](./CODE_OF_CONDUCT.md). Please keep interactions professional, welcoming, and constructive.

Thank you for helping make generative AI safer for everyone!
