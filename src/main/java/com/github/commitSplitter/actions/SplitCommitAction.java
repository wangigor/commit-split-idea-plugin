package com.github.commitSplitter.actions;

import com.github.commitSplitter.services.CommitSplitterService;
import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.github.commitSplitter.ui.RemoteConfigDialog;
import com.github.commitSplitter.utils.GitUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SplitCommitAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        
        // 检查设置
        CommitSplitterSettings settings = CommitSplitterSettings.getInstance();
        if (settings.users.isEmpty()) {
            Messages.showWarningDialog(project,
                    "Please configure users in Settings → Tools → Commit Splitter first.",
                    "No Users Configured");
            return;
        }
        
        // 获取选中的commit
        SelectedCommit selectedCommit = getSelectedCommit(e);
        if (selectedCommit == null) {
            Messages.showErrorDialog(project,
                    "No commit selected or unable to get commit hash.",
                    "Invalid Selection");
            return;
        }

        // 获取Git仓库
        GitRepository repository = getGitRepository(project, selectedCommit);
        if (repository == null) {
            Messages.showErrorDialog(project,
                    "Current project is not a Git repository.",
                    "Not a Git Repository");
            return;
        }
        
        // 检查仓库状态 - 简化检查，只要repository不为null即可
        // 在2024.2版本中，GitRepository.State.NORMAL已被移除
        
        // 显示远程配置对话框
        showRemoteConfigAndSplit(project, repository, selectedCommit.hash(), settings);
    }

    private void showRemoteConfigAndSplit(Project project, GitRepository repository,
                                         String commitHash, CommitSplitterSettings settings) {
        boolean needsPush = settings.users.stream()
                .anyMatch(user -> user.getPassword() != null && !user.getPassword().trim().isEmpty());

        String commitMessage;
        try {
            commitMessage = GitUtils.getCommitMessage(repository, commitHash);
        } catch (Exception e) {
            Messages.showErrorDialog(project, "Failed to read commit message: " + e.getMessage(), "Error");
            return;
        }

        RemoteConfigDialog dialog = new RemoteConfigDialog(project, repository, settings.users, needsPush, commitMessage);
        if (dialog.showAndGet()) {
            List<CommitSplitterSettings.UserInfo> selectedUsers = dialog.getSelectedUsers();
            RemoteConfig remoteConfig = null;
            if (needsPush) {
                remoteConfig = new RemoteConfig(
                        dialog.getSelectedRemote(),
                        dialog.getSelectedBranch()
                );
            }
            executeSplit(project, repository, commitHash, settings, remoteConfig, dialog.getUserMessages(), selectedUsers);
        }
    }

    private void executeSplit(Project project, GitRepository repository,
                              String commitHash, CommitSplitterSettings settings,
                              RemoteConfig remoteConfig, java.util.Map<String, String> userMessages,
                              List<CommitSplitterSettings.UserInfo> selectedUsers) {
        CommitSplitterService service = new CommitSplitterService(project, repository);
        service.splitCommit(commitHash, settings, remoteConfig, userMessages, selectedUsers);
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = false;
        boolean visible = false;
        
        if (project != null) {
            // 检查是否有选中的commit
            SelectedCommit selectedCommit = getSelectedCommit(e);
            if (selectedCommit != null) {
                // 检查是否是Git项目
                GitRepository repository = getGitRepository(project, selectedCommit);
                if (repository != null) {
                    enabled = true;
                    visible = true;
                }
            }
        }
        
        e.getPresentation().setEnabled(enabled);
        e.getPresentation().setVisible(visible);
        
        // 确保只有我们的Split Commit action显示，其他的不相关action保持隐藏
        if (visible) {
            e.getPresentation().setText("Split Commit");
            e.getPresentation().setDescription("Split this commit into multiple commits for different users");
        }
    }
    
    private SelectedCommit getSelectedCommit(@NotNull AnActionEvent e) {
        // 尝试从VCS Log获取
        VcsLog vcsLog = e.getData(VcsLogDataKeys.VCS_LOG);
        if (vcsLog != null) {
            var selection = vcsLog.getSelectedCommits();
            if (!selection.isEmpty()) {
                var commit = selection.get(0);
                return new SelectedCommit(commit.getHash().asString(), commit.getRoot(), true);
            }
        }
        
        // 尝试从VCS历史获取
        VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
        if (revisions != null && revisions.length > 0) {
            String hash = revisions[0].getRevisionNumber().asString();
            VirtualFile file = e.getProject() != null
                    ? e.getProject().getBaseDir()
                    : null;
            return new SelectedCommit(hash, file, false);
        }
        
        return null;
    }
    
    private GitRepository getGitRepository(@NotNull Project project, SelectedCommit selectedCommit) {
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);

        if (selectedCommit != null && selectedCommit.location() != null) {
            GitRepository repoByLocation = selectedCommit.isRepoRoot()
                    ? repositoryManager.getRepositoryForRootQuick(selectedCommit.location())
                    : repositoryManager.getRepositoryForFileQuick(selectedCommit.location());

            if (repoByLocation != null) {
                return repoByLocation;
            }
        }

        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile baseDir = project.getBaseDir();
            GitRepository repoForBase = baseDir != null
                    ? repositoryManager.getRepositoryForRootQuick(baseDir)
                    : null;
            if (repoForBase != null) {
                return repoForBase;
            }
        }

        var repositories = repositoryManager.getRepositories();
        return repositories.isEmpty() ? null : repositories.get(0);
    }

    private static class SelectedCommit {
        private final String hash;
        private final VirtualFile location;
        private final boolean isRepoRoot;

        SelectedCommit(String hash, VirtualFile location, boolean isRepoRoot) {
            this.hash = hash;
            this.location = location;
            this.isRepoRoot = isRepoRoot;
        }

        String hash() { return hash; }
        VirtualFile location() { return location; }
        boolean isRepoRoot() { return isRepoRoot; }
    }
}
