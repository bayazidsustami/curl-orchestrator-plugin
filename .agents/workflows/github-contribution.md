---
description: Create a feature or bugfix on a new branch and create a PR
---

This workflow is for contributing new features, bug fixes, or hotfixes to the repository. Follow these exact steps:

1. Make sure the local repository is on the main branch and up to date:
   ```bash
   git checkout main && git pull origin main
   ```
2. Checkout a new branch using the pattern `<type>/<name-of-changes>`, where `<type>` MUST be one of `feature`, `bugfix`, or `hotfix`:
   ```bash
   git checkout -b feature/my-cool-feature
   ```
3. Perform the necessary file edits, compiling, and testing to implement the changes.
4. Once the work is verified and complete, stage the modified files:
   ```bash
   git add .
   ```
5. Create a **semantic commit**, ensuring the message accurately describes the change (using conventional commits like `feat:`, `fix:`, or `chore:`):
   ```bash
   git commit -m "feat: <description of changes>"
   ```
6. Push the branch to the remote repository and set the upstream tracking branch:
   ```bash
   git push -u origin <branch-name> 
   ```
7. Finally, Create a Pull Request (PR) against the default branch. You **MUST** include a detailed PR description of what was added or fixed and why. You should use the `mcp_github-mcp-server_create_pull_request` tool if available, or the `gh pr create` command directly via `run_command` to open the PR.
