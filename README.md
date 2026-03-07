# Curl Orchestrator Plugin

🚀 **Curl Orchestrator** is a powerful Android Studio and IntelliJ-based IDE plugin that brings Postman-like API testing capabilities directly to your code editor, powered by your machine's native `curl` engine.

## ✨ Features

- **Native Execution**: Leverages your system's native `curl` executable to ensure accurate, real-world HTTP connections without the overhead of heavy internalized Java HTTP clients.
- **REST Request Editor**: Full UI support for configuring `URL`, `Method`, `Headers`, `Query Parameters`, and `Raw Body` payloads.
- **Form Data & File Uploads**: Natively attach and upload local files (`multipart/form-data`) seamlessly through the UI.
- **Image Previews**: If the endpoint returns an image (e.g., `image/png`, `image/jpeg`), the plugin instantly renders a visual preview pane.
- **Dynamic History Sidebar**: Every request you make is saved chronologically. The sidebar intelligently snaps its layout based on where you dock the tool window in your IDE (bottom or sides).
- **Import/Export Schema**: Easily serialize and share complex requests as `.json` files. Import them back to quickly populate the GUI.
- **Intelligent Response UI**: Format JSON payloads dynamically, view raw curl logs to see the exact command executed, and inspect response headers separated clearly from the body.

## 🛠️ Installation

### From IDE 

1. Open IntelliJ IDEA or Android Studio.
2. Go to **Preferences/Settings > Plugins > Marketplace**.
3. Search for "Curl Orchestrator".
4. Click **Install**.

### From GitHub Release
1. Go to the [Releases](https://github.com/bayazidsustami/curl-orchestrator-plugin/releases) page.
2. Download the `.zip` archive of the latest version.
3. Open your IDE to **Settings > Plugins**.
4. Click the gear icon `⚙️` > **Install Plugin from Disk...**
5. Select the downloaded `.zip` file.

## 🤝 Contributing

We welcome contributions from fellow developers and AI agents! 

Before contributing, please read our [GitHub Contribution Workflow](.agents/workflows/github-contribution.md) to understand our branching and PR policies.

### Local Development Setup
1. Clone the repository: `git clone https://github.com/bayazidsustami/curl-orchestrator-plugin.git`
2. Open the project in IntelliJ IDEA.
3. The project uses Gradle. Wait for it to sync.
4. Run the Sandbox IDE: 
   ```bash
   ./gradlew runIde
   ```

### Creating a Pull Request
We use semantic versioning and conventional commits (e.g., `feat:`, `fix:`, `chore:`). Ensure you open your PR against the `main` branch and fill out the provided PR template.

## 📜 License
This project is licensed under the MIT License.
