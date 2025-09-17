package com.github.commitSplitter.services;

import com.github.commitSplitter.strategy.SplitStrategy;
import com.github.commitSplitter.strategy.SplitStrategyFactory;
import com.github.commitSplitter.utils.GitUtils;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.commands.*;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class CommitSplitterService {
    private final Project project;
    private final GitRepository repository;
    private final git4idea.commands.Git git;
    
    public CommitSplitterService(Project project, GitRepository repository) {
        this.project = project;
        this.repository = repository;
        this.git = git4idea.commands.Git.getInstance();
    }
    
    public void splitCommit(String commitHash, CommitSplitterSettings settings) {
        splitCommit(commitHash, settings, null);
    }
    
    public void splitCommit(String commitHash, CommitSplitterSettings settings, RemoteConfig remoteConfig) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Splitting Commit", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    indicator.setText("Validating commit and working tree...");
                    indicator.setFraction(0.1);
                    
                    // 验证状态
                    validateCommitAndWorkTree(commitHash);
                    
                    indicator.setText("Analyzing commit...");
                    indicator.setFraction(0.2);
                    
                    // 获取commit信息
                    String commitMessage = GitUtils.getCommitMessage(repository, commitHash);
                    List<String> modifiedFiles = GitUtils.getModifiedFiles(repository, commitHash);
                    
                    indicator.setText("Determining split strategy...");
                    indicator.setFraction(0.3);
                    
                    // 创建策略并执行
                    SplitStrategy strategy = SplitStrategyFactory.createStrategy(settings, modifiedFiles, commitHash, project, repository);
                    
                    indicator.setText("Executing split strategy: " + strategy.getStrategyName());
                    try {
                        strategy.execute(project, repository, commitHash, commitMessage, modifiedFiles, settings.users, indicator, remoteConfig);
                    } catch (Exception strategyException) {
                        System.err.println("Strategy execution failed: " + strategyException.getMessage());
                        strategyException.printStackTrace();
                        throw new RuntimeException("Strategy execution failed: " + strategyException.getMessage(), strategyException);
                    }
                    
                    indicator.setText("Commit splitting completed successfully!");
                    indicator.setFraction(1.0);
                    
                    // 刷新VCS Log
                    refreshVcsLog();
                    
                    // 显示成功消息
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 检查是否有用户配置了密码（意味着尝试推送）
                        long usersWithPassword = settings.users.stream()
                                .filter(user -> user.getPassword() != null && !user.getPassword().trim().isEmpty())
                                .count();
                        
                        String message = String.format("Commit %s has been split into %d commits using %s strategy.",
                                commitHash.substring(0, 8), settings.users.size(), strategy.getStrategyName());
                        
                        if (usersWithPassword > 0) {
                            message += String.format("\n\nPush status: Attempted to push for %d user(s) with configured credentials.", 
                                    usersWithPassword);
                        } else {
                            message += "\n\nNo push performed (no credentials configured).";
                        }
                        
                        Messages.showInfoMessage(project, message, "Split Successful");
                    });
                    
                } catch (Exception e) {
                    indicator.setText("Split failed: " + e.getMessage());
                    e.printStackTrace(); // 添加详细的堆栈跟踪
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project,
                                "Failed to split commit: " + e.getMessage() + 
                                "\n\nCause: " + (e.getCause() != null ? e.getCause().getMessage() : "Unknown"),
                                "Split Failed");
                    });
                }
            }
        });
    }
    
    private void validateCommitAndWorkTree(String commitHash) throws Exception {
        // 检查commit是否存在
        if (!commitExists(commitHash)) {
            throw new IllegalArgumentException("Commit not found: " + commitHash);
        }
        
        // 检查工作树是否干净
        if (!GitUtils.isWorkingTreeClean(repository)) {
            throw new IllegalStateException("Working tree is not clean. Please commit or stash your changes.");
        }
    }
    
    private boolean commitExists(String commitHash) {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.CAT_FILE);
        handler.addParameters("-e", commitHash);
        handler.setSilent(true);
        
        GitCommandResult result = git.runCommand(handler);
        return result.success();
    }
    
    /**
     * 刷新VCS Log视图
     */
    private void refreshVcsLog() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // 使用更安全的方式刷新VCS状态
                // 避免使用可能导致空指针异常的tryToExecute方法
                repository.update();
                System.out.println("Repository state refreshed successfully");
            } catch (Exception e) {
                System.err.println("Failed to refresh repository state: " + e.getMessage());
            }
        });
    }
}