package com.github.commitSplitter.services;

import com.github.commitSplitter.strategy.SplitStrategy;
import com.github.commitSplitter.strategy.SplitStrategyFactory;
import com.github.commitSplitter.utils.GitUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        splitCommit(commitHash, settings, null, Collections.emptyMap(), settings.users);
    }

    public void splitCommit(String commitHash, CommitSplitterSettings settings, RemoteConfig remoteConfig) {
        splitCommit(commitHash, settings, remoteConfig, Collections.emptyMap(), settings.users);
    }

    public void splitCommit(String commitHash, CommitSplitterSettings settings,
                             RemoteConfig remoteConfig, Map<String, String> userMessages) {
        splitCommit(commitHash, settings, remoteConfig, userMessages, settings.users);
    }

    public void splitCommit(String commitHash, CommitSplitterSettings settings,
                             RemoteConfig remoteConfig, Map<String, String> userMessages,
                             List<CommitSplitterSettings.UserInfo> selectedUsers) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Splitting Commit", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                try {
                    indicator.setText("Validating commit and working tree...");
                    indicator.setFraction(0.1);

                    validateCommitAndWorkTree(commitHash);

                    indicator.setText("Analyzing commit...");
                    indicator.setFraction(0.2);

                    List<String> modifiedFiles = GitUtils.getModifiedFiles(repository, commitHash);

                    indicator.setText("Determining split strategy...");
                    indicator.setFraction(0.3);

                    SplitStrategy strategy = SplitStrategyFactory.createStrategy(settings, modifiedFiles, commitHash, project, repository, selectedUsers);

                    indicator.setText("Executing split strategy: " + strategy.getStrategyName());
                    try {
                        Map<String, String> effectiveMessages = userMessages != null
                                ? userMessages
                                : Collections.emptyMap();
                        strategy.execute(project, repository, commitHash, modifiedFiles,
                                selectedUsers, indicator, remoteConfig, effectiveMessages);
                    } catch (Exception strategyException) {
                        System.err.println("Strategy execution failed: " + strategyException.getMessage());
                        strategyException.printStackTrace();
                        throw new RuntimeException("Strategy execution failed: " + strategyException.getMessage(), strategyException);
                    }

                    indicator.setText("Commit splitting completed successfully!");
                    indicator.setFraction(1.0);

                    refreshVcsLog();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        long usersWithPassword = selectedUsers.stream()
                                .filter(user -> user.getPassword() != null && !user.getPassword().trim().isEmpty())
                                .count();

                        String message = String.format("Commit %s has been split into %d commits using %s strategy.",
                                commitHash.substring(0, 8), selectedUsers.size(), strategy.getStrategyName());

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
                    e.printStackTrace();
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
        if (!commitExists(commitHash)) {
            throw new IllegalArgumentException("Commit not found: " + commitHash +
                    ". Ensure the commit is local and reachable (e.g. in reflog) before splitting.");
        }

        if (!GitUtils.isWorkingTreeClean(repository)) {
            throw new IllegalStateException("Working tree is not clean. Please commit or stash your changes.");
        }
    }

    private boolean commitExists(String commitHash) {
        if (commitHash == null || commitHash.trim().isEmpty()) {
            return false;
        }

        try {
            GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.CAT_FILE);
            handler.addParameters("-e", commitHash);
            handler.setSilent(true);

            GitCommandResult result = git.runCommand(handler);
            if (result.success()) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return GitUtils.commitExists(repository, commitHash);
    }

    private void refreshVcsLog() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                repository.update();
                System.out.println("Repository state refreshed successfully");
            } catch (Exception e) {
                System.err.println("Failed to refresh repository state: " + e.getMessage());
            }
        });
    }
}
