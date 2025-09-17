package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.github.commitSplitter.utils.GitUtils;
import com.github.commitSplitter.utils.HunkApplier;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.commands.*;
import git4idea.repo.GitRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HunkBasedSplitStrategy extends AbstractSplitStrategy {
    
    @Override
    public void execute(Project project, GitRepository repository, String commitHash, 
                       String commitMessage, List<String> modifiedFiles, 
                       List<CommitSplitterSettings.UserInfo> users, ProgressIndicator indicator, 
                       RemoteConfig remoteConfig) throws Exception {
        
        // 收集所有hunks
        List<GitUtils.CustomHunk> allHunks = collectAllHunks(project, repository, commitHash, modifiedFiles);
        
        if (allHunks.isEmpty()) {
            throw new RuntimeException("No hunks found in commit");
        }
        
        // 获取父commit
        String parentCommit = GitUtils.getParentCommitHash(repository, commitHash);
        
        // 按文件和行号排序hunks，保持文件内的修改顺序
        allHunks.sort((h1, h2) -> {
            int fileCompare = h1.getFilePath().compareTo(h2.getFilePath());
            if (fileCompare != 0) return fileCompare;
            return Integer.compare(h1.getStartLineNumber(), h2.getStartLineNumber());
        });
        
        // 计算每用户分配的hunk数量
        int totalHunks = allHunks.size();
        int hunksPerUser = Math.max(1, totalHunks / users.size());
        int remainder = totalHunks % users.size();
        
        // 重置到父commit状态
        resetToParent(project, repository, parentCommit);
        
        HunkApplier hunkApplier = new HunkApplier(project, repository);
        int startHunkIndex = 0;
        
        for (int userIndex = 0; userIndex < users.size(); userIndex++) {
            CommitSplitterSettings.UserInfo user = users.get(userIndex);
            
            indicator.setText(String.format("Processing hunks for user %s (%d/%d)...", 
                            user.username, userIndex + 1, users.size()));
            
            // 计算当前用户处理的hunk数量
            int currentHunksCount = hunksPerUser;
            if (userIndex < remainder) {
                currentHunksCount += 1;
            }
            
            int endHunkIndex = Math.min(startHunkIndex + currentHunksCount, totalHunks);
            
            // 应用分配给当前用户的hunks
            boolean hasChanges = false;
            for (int i = startHunkIndex; i < endHunkIndex && i < allHunks.size(); i++) {
                GitUtils.CustomHunk hunk = allHunks.get(i);
                try {
                    System.out.println(String.format("Applying hunk %d/%d for user %s: %s", 
                        i + 1, allHunks.size(), user.username, hunk.getFilePath()));
                    hunkApplier.applyHunk(hunk);
                    hasChanges = true;
                    System.out.println("Successfully applied hunk " + (i + 1));
                } catch (Exception e) {
                    System.err.println("Failed to apply hunk " + (i + 1) + " for file " + 
                        hunk.getFilePath() + ": " + e.getMessage());
                    e.printStackTrace();
                    // 继续处理其他hunks，而不是中断整个过程
                }
            }
            
            // 如果有变更，则创建提交
            if (hasChanges) {
                hunkApplier.addFiles();
                String newMessage = processCommitMessage(commitMessage, user.username);
                hunkApplier.createCommit(newMessage, user);
                
                // 立即推送该用户的commit（使用该用户的凭据）
                pushToRemote(project, repository, user, remoteConfig);
                
            }
            
            startHunkIndex = endHunkIndex;
            indicator.setFraction(0.4 + 0.5 * (userIndex + 1) / users.size());
        }
    }
    
    @Override
    public boolean canExecute(List<String> modifiedFiles, int userCount) {
        // 简化检查：如果有修改的文件就可以尝试
        return !modifiedFiles.isEmpty();
    }
    
    @Override
    public String getStrategyName() {
        return "HUNKS";
    }
    
    /**
     * 收集所有hunks
     */
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
    
    /**
     * 重置到父commit
     */
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