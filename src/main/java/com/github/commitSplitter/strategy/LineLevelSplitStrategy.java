package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.github.commitSplitter.utils.GitUtils;
import com.github.commitSplitter.utils.HunkApplier;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LineLevelSplitStrategy extends AbstractSplitStrategy {

    @Override
    public void execute(Project project, GitRepository repository, String commitHash,
                       List<String> modifiedFiles,
                       List<CommitSplitterSettings.UserInfo> users, ProgressIndicator indicator,
                       RemoteConfig remoteConfig, Map<String, String> userMessages) throws Exception {

        List<GitUtils.CustomHunk> allHunks = collectAllHunks(project, repository, commitHash, modifiedFiles);

        if (allHunks.isEmpty()) {
            throw new RuntimeException("No hunks found in commit");
        }

        String parentCommit = GitUtils.getParentCommitHash(repository, commitHash);

        allHunks.sort((h1, h2) -> {
            int fileCompare = h1.getFilePath().compareTo(h2.getFilePath());
            if (fileCompare != 0) return fileCompare;
            return Integer.compare(h1.getStartNewLine(), h2.getStartNewLine());
        });

        int totalLines = allHunks.stream().mapToInt(GitUtils.CustomHunk::getChangeLineCount).sum();
        int linesPerUser = Math.max(1, totalLines / users.size());
        int remainder = totalLines % users.size();

        resetToParent(project, repository, parentCommit);

        HunkApplier hunkApplier = new HunkApplier(project, repository);
        int currentLineIndex = 0;

        for (int userIndex = 0; userIndex < users.size(); userIndex++) {
            CommitSplitterSettings.UserInfo user = users.get(userIndex);

            indicator.setText(String.format("Processing lines for user %s (%d/%d)...",
                            user.username, userIndex + 1, users.size()));

            int targetLines = linesPerUser + (userIndex < remainder ? 1 : 0);
            int accumulatedLines = 0;
            boolean hasChanges = false;

            for (int i = 0; i < allHunks.size() && accumulatedLines < targetLines; i++) {
                GitUtils.CustomHunk hunk = allHunks.get(i);
                if (accumulatedLines >= targetLines) break;

                if (hunk.getChangeLineCount() == 0) {
                    accumulatedLines += 1;
                } else {
                    accumulatedLines += hunk.getChangeLineCount();
                }

                if (accumulatedLines > targetLines && i > 0) {
                    accumulatedLines -= hunk.getChangeLineCount();
                    continue;
                }

                try {
                    hunkApplier.applyHunk(hunk);
                    hasChanges = true;
                } catch (Exception e) {
                    System.err.println("Failed to apply hunk " + (i + 1) + " for file " +
                        hunk.getFilePath() + ": " + e.getMessage());
                }
            }

            if (hasChanges) {
                hunkApplier.addFiles();
                String message = userMessages.get(user.username);
                hunkApplier.createCommit(message != null ? message : "", user);
                pushToRemote(project, repository, user, remoteConfig);
            }

            indicator.setFraction(0.4 + 0.5 * (userIndex + 1) / users.size());
        }
    }

    @Override
    public boolean canExecute(List<String> modifiedFiles, int userCount) {
        return !modifiedFiles.isEmpty();
    }

    @Override
    public String getStrategyName() {
        return "LINES";
    }

    private List<GitUtils.CustomHunk> collectAllHunks(Project project, GitRepository repository,
                                                      String commitHash, List<String> modifiedFiles) throws Exception {
        List<GitUtils.CustomHunk> allHunks = new ArrayList<>();

        for (String file : modifiedFiles) {
            try {
                List<GitUtils.CustomHunk> fileHunks = GitUtils.getCustomHunksUsingGitCli(project, repository, commitHash, file);
                for (GitUtils.CustomHunk hunk : fileHunks) {
                    hunk.setCommitHash(commitHash);
                    allHunks.add(hunk);
                }
            } catch (Exception e) {
                System.err.println("Failed to get hunks for file " + file + ": " + e.getMessage());
            }
        }

        return allHunks;
    }

    private void resetToParent(Project project, GitRepository repository, String parentCommit) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.RESET);
        handler.addParameters("--hard", parentCommit);

        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new RuntimeException("Failed to reset to parent commit: " +
                                     String.join("\n", result.getErrorOutput()));
        }
    }
}
