package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;

import java.util.List;
import java.util.Map;

public interface SplitStrategy {

    void execute(Project project, GitRepository repository, String commitHash,
                List<String> modifiedFiles,
                List<CommitSplitterSettings.UserInfo> users, ProgressIndicator indicator,
                RemoteConfig remoteConfig, Map<String, String> userMessages) throws Exception;

    boolean canExecute(List<String> modifiedFiles, int userCount);

    String getStrategyName();
}
