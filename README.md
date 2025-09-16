# Commit Splitter Plugin for IntelliJ IDEA

A powerful IntelliJ IDEA plugin that allows you to split a single Git commit into multiple commits assigned to different users, with automatic push functionality and intelligent hunk-based splitting.

## Features

🚀 **Smart Splitting Strategies**
- **By Files**: Distribute modified files among users
- **By Hunks**: Split code changes by logical Git hunks (patches)
- **Automatic Strategy Selection**: Intelligently choose between files and hunks mode

🔧 **Advanced Configuration**
- Manage multiple users with usernames, emails, and push credentials
- Remote repository and branch selection for each split operation
- Automatic push functionality with individual user authentication
- Intelligent commit message processing with @username prefixes

⚡ **Seamless Integration**
- Right-click any commit in Git log to split it
- Real-time progress tracking with detailed preview
- Clean Git history maintenance
- Dual patch application system (JGit + git command fallback)
- Smart path correction for multi-module projects

## Installation

### From Plugin Repository (Recommended)
1. Open IntelliJ IDEA
2. Go to `File → Settings → Plugins`
3. Search for "Commit Splitter"
4. Install and restart IDE

### Manual Installation
1. Download the latest release from [GitHub Releases](https://github.com/username/commit-splitter-plugin/releases)
2. Go to `File → Settings → Plugins`
3. Click the gear icon → `Install Plugin from Disk...`
4. Select the downloaded `.zip` file

## Quick Start

### 1. Configure Users
1. Go to `File → Settings → Tools → Commit Splitter`
2. Add users with their usernames and emails
3. Optionally add Personal Access Tokens for automatic push functionality
4. Configure push settings (if using remote repositories)

### 2. Split a Commit
1. Open the Git log (`View → Tool Windows → Git`)
2. Right-click on any commit
3. Select `Split Commit`
4. If users have push credentials, configure remote repository and target branch
5. Review the preview and click `OK` to execute

### 3. Result
The original commit is replaced with multiple commits, each:
- Assigned to a different user (author and committer)
- Containing appropriate changes (files or hunks)
- Automatically pushed to the selected remote branch (if configured)
- Tagged with @username prefixes in commit messages

## Configuration

### User Management
```
Username: john.doe
Email: john.doe@company.com
Password/Token: ghp_xxxxxxxxxxxx (Optional - for push functionality)

Username: jane.smith  
Email: jane.smith@company.com
Password/Token: ghp_yyyyyyyyyyyy (Optional - for push functionality)
```

### Split Strategies
- **Files Mode**: Distributes entire files among users
  - Best when multiple files are modified
  - Each user gets complete files with all their changes
- **Hunks Mode**: Splits changes by Git hunks (logical code patches)
  - Best for single files with multiple logical changes  
  - Each user gets individual hunks from the original commit
  - Uses advanced patch application with JGit and git apply fallback

### Commit Message Processing
The plugin intelligently processes commit messages:
- Existing `@username` prefix → replaces with current user
- Other formats → adds `@username ` prefix
- Maintains original commit message content

## Examples

### Before Splitting
```
commit d049251027 (Original Author)
@jixn @202504221940-关于新增7天犹豫期业务退订场景及流程的需求申请优化v1

Modified files:
- aitask-inter/src/main/java/.../PagesStaticParamController.java
  - Import statement changes (4 lines removed, 1 line added)
  - Code logic changes (1 line removed, 2 lines added)
```

### After Splitting (Hunks Mode)
```
commit abc123def (tiansj <tiansj@asiainfo.com>)
@tiansj @202504221940-关于新增7天犹豫期业务退订场景及流程的需求申请优化v1
- Import statement refactoring: replaced individual imports with wildcard

commit def456ghi (yangxu3 <yangxu3@asiainfo.com>)
@yangxu3 @202504221940-关于新增7天犹豫期业务退订场景及流程的需求申请优化v1
- Code logic update: changed Arrays.asList to ArrayList with explicit add
```

### Remote Push Configuration
When users have push credentials configured, the plugin will:
1. Show a remote configuration dialog before splitting
2. Allow selection of target remote repository and branch
3. Automatically push each split commit using the respective user's credentials
4. Handle authentication via Personal Access Tokens or passwords

## Requirements
- IntelliJ IDEA 2023.2 or later
- Git repository
- Java 17 or later

## Development

### Building from Source
```bash
git clone https://github.com/username/commit-splitter-plugin.git
cd commit-splitter-plugin
./gradlew buildPlugin
```

### Running in Development
```bash
./gradlew runIde
```

## Contributing
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/username/commit-splitter-plugin/issues)
- 💡 **Feature Requests**: [GitHub Discussions](https://github.com/username/commit-splitter-plugin/discussions)
- 📧 **Email**: support@commitsplitter.com

