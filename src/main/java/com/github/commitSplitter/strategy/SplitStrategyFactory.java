package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.utils.GitUtils;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;

import java.util.List;

public class SplitStrategyFactory {

    public static SplitStrategy createStrategy(CommitSplitterSettings settings, List<String> modifiedFiles,
                                             String commitHash, Project project, GitRepository repository,
                                             List<CommitSplitterSettings.UserInfo> selectedUsers) throws Exception {

        try {
            if (settings.defaultStrategy != CommitSplitterSettings.SplitStrategy.AUTO) {
                return createStrategyByType(settings.defaultStrategy);
            }

            int fileCount = modifiedFiles.size();
            int userCount = selectedUsers.size();

            System.out.println("Strategy selection: " + fileCount + " files, " + userCount + " selected users");

            FileBasedSplitStrategy fileStrategy = new FileBasedSplitStrategy();
            if (fileStrategy.canExecute(modifiedFiles, userCount)) {
                System.out.println("Selected FILES strategy");
                return fileStrategy;
            }

            int totalHunks = getTotalHunkCount(project, repository, commitHash, modifiedFiles);
            System.out.println("Total hunks found: " + totalHunks);

            if (totalHunks >= userCount) {
                System.out.println("Selected HUNKS strategy");
                return new HunkBasedSplitStrategy();
            }

            int totalLines = getTotalLineCount(project, repository, commitHash, modifiedFiles);
            System.out.println("Total change lines found: " + totalLines);

            if (totalLines >= userCount) {
                System.out.println("Selected LINES strategy");
                return new LineLevelSplitStrategy();
            }

            throw new RuntimeException(String.format(
                "Cannot split commit: only %d files, %d hunks, %d change lines available for %d users.",
                fileCount, totalHunks, totalLines, userCount));

        } catch (Exception e) {
            System.err.println("Failed to create strategy: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create split strategy: " + e.getMessage(), e);
        }
    }

    public static SplitStrategy createStrategyByType(CommitSplitterSettings.SplitStrategy strategyType) {
        switch (strategyType) {
            case FILES:
                return new FileBasedSplitStrategy();
            case HUNKS:
                return new HunkBasedSplitStrategy();
            case LINES:
                return new LineLevelSplitStrategy();
            default:
                throw new IllegalArgumentException("Unsupported strategy: " + strategyType);
        }
    }

    private static int getTotalHunkCount(Project project, GitRepository repository,
                                       String commitHash, List<String> modifiedFiles) throws Exception {
        int totalHunks = 0;
        for (String file : modifiedFiles) {
            try {
                int hunkCount = GitUtils.getHunkCountUsingGitCli(project, repository, commitHash, file);
                totalHunks += hunkCount;
            } catch (Exception e) {
            }
        }
        return totalHunks;
    }

    private static int getTotalLineCount(Project project, GitRepository repository,
                                       String commitHash, List<String> modifiedFiles) throws Exception {
        int totalLines = 0;
        for (String file : modifiedFiles) {
            try {
                List<GitUtils.CustomHunk> hunks = GitUtils.getCustomHunksUsingGitCli(project, repository, commitHash, file);
                for (GitUtils.CustomHunk hunk : hunks) {
                    totalLines += hunk.getChangeLineCount();
                }
            } catch (Exception e) {
            }
        }
        return totalLines;
    }
}