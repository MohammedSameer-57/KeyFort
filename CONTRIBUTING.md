# Contributing to KeyFort 🤝

Thank you for your interest in helping improve KeyFort! Whether you're fixing a bug, improving documentation, or proposing new features, your help is very welcome.

---

## 🌟 How You Can Help

1. **Reporting Bugs:** If you discover a bug or unexpected behavior, please check open issues first. If it hasn't been reported, open a new issue describing what happened and how to reproduce it.
2. **Suggesting Enhancements:** Have an idea to make KeyFort faster, more secure, or easier to use? Open a feature request to start a discussion.
3. **Submitting Pull Requests:** Ready to submit code? Follow the simple workflow below.

---

## 🚀 Pull Request Workflow

1. **Fork the Repository:** Create your own copy of the project on GitHub.
2. **Clone your fork locally:**
   ```bash
   git clone https://github.com/<your-username>/KeyFort.git
   cd KeyFort
   ```
3. **Create a new branch:** Use a descriptive branch name:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/bug-description
   ```
4. **Make your changes:** Keep changes focused on the specific goal.
5. **Run tests before committing:** Make sure all tests pass without errors:
   ```bash
   # Windows
   gradlew.bat test

   # Linux / macOS
   ./gradlew test
   ```
6. **Commit your changes:** Write clear, conversational commit messages:
   ```bash
   git commit -m "Add biometric timeout configuration to Android settings"
   ```
7. **Push to your fork and submit PR:**
   ```bash
   git push origin feature/your-feature-name
   ```
   Then open a Pull Request against the `main` branch.

---

## 📜 Code Style Guidelines

- **Java 17+ Standards:** Follow standard clean code principles (clear variable names, minimal unnecessary complexity).
- **Zero-Knowledge Core Rule:** Never log unencrypted master secrets, plaintext passwords, or raw cryptographic keys in `keyfort-core` or `keyfort-server`.
- **Formatting:** Keep indentation consistent with existing files (4 spaces for Java and Gradle).

Thank you for helping keep passwords safe and private for everyone!
