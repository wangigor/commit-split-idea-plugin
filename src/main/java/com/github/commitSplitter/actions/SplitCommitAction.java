package com.github.commitSplitter.actions;

import com.github.commitSplitter.services.CommitSplitterService;
import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.github.commitSplitter.ui.RemoteConfigDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

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
        String commitHash = getSelectedCommitHash(e);
        if (commitHash == null) {
            Messages.showErrorDialog(project,
                    "No commit selected or unable to get commit hash.",
                    "Invalid Selection");
            return;
        }
        
        // 获取Git仓库
        GitRepository repository = getGitRepository(project);
        if (repository == null) {
            Messages.showErrorDialog(project,
                    "Current project is not a Git repository.",
                    "Not a Git Repository");
            return;
        }
        
        // 检查仓库状态 - 简化检查，只要repository不为null即可
        // 在2024.2版本中，GitRepository.State.NORMAL已被移除
        
        // 显示远程配置对话框
        showRemoteConfigAndSplit(project, repository, commitHash, settings);
    }
    
    private void showRemoteConfigAndSplit(Project project, GitRepository repository, 
                                         String commitHash, CommitSplitterSettings settings) {
        // 检查是否有用户配置了密码（意味着需要推送）
        boolean needsPush = settings.users.stream()
                .anyMatch(user -> user.getPassword() != null && !user.getPassword().trim().isEmpty());
        
        RemoteConfigDialog dialog = new RemoteConfigDialog(project, repository, settings.users, needsPush);
        if (dialog.showAndGet()) {
            // 用户点击了确定，获取配置并执行拆分
            RemoteConfig remoteConfig = null;
            if (needsPush) {
                remoteConfig = new RemoteConfig(
                        dialog.getSelectedRemote(),
                        dialog.getSelectedBranch()
                );
            }
            executeSplit(project, repository, commitHash, settings, remoteConfig, dialog.getUserPrefixes());
        }
        // 如果用户取消，则不执行任何操作
    }

    private void executeSplit(Project project, GitRepository repository, 
                              String commitHash, CommitSplitterSettings settings,
                              RemoteConfig remoteConfig, java.util.Map<String, String> userPrefixes) {
        CommitSplitterService service = new CommitSplitterService(project, repository);
        service.splitCommit(commitHash, settings, remoteConfig, userPrefixes);
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = false;
        boolean visible = false;
        
        if (project != null) {
            // 检查是否有选中的commit
            String commitHash = getSelectedCommitHash(e);
            if (commitHash != null) {
                // 检查是否是Git项目
                GitRepository repository = getGitRepository(project);
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
    
    private String getSelectedCommitHash(@NotNull AnActionEvent e) {
        // 尝试从VCS Log获取
        VcsLog vcsLog = e.getData(VcsLogDataKeys.VCS_LOG);
        if (vcsLog != null) {
            var selection = vcsLog.getSelectedCommits();
            if (!selection.isEmpty()) {
                return selection.get(0).getHash().asString();
            }
        }
        
        // 尝试从VCS历史获取
        VcsFileRevision[] revisions = e.getData(VcsDataKeys.VCS_FILE_REVISIONS);
        if (revisions != null && revisions.length > 0) {
            return revisions[0].getRevisionNumber().asString();
        }
        
        return null;
    }
    
    private GitRepository getGitRepository(@NotNull Project project) {
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        var repositories = repositoryManager.getRepositories();
        return repositories.isEmpty() ? null : repositories.get(0);
    }
}
