package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.commands.*;
import git4idea.repo.GitRepository;

import java.io.File;
import java.util.List;

public class FileBasedSplitStrategy extends AbstractSplitStrategy {
    
    @Override
    public void execute(Project project, GitRepository repository, String commitHash, 
                       String commitMessage, List<String> modifiedFiles, 
                       List<CommitSplitterSettings.UserInfo> users, ProgressIndicator indicator, 
                       RemoteConfig remoteConfig) throws Exception {
        
        // 获取父commit并重置到父commit状态
        String parentCommit = com.github.commitSplitter.utils.GitUtils.getParentCommitHash(repository, commitHash);
        resetToParent(project, repository, parentCommit);
        
        int filesPerUser = Math.max(1, modifiedFiles.size() / users.size());
        int currentFileIndex = 0;
        
        for (int userIndex = 0; userIndex < users.size(); userIndex++) {
            CommitSplitterSettings.UserInfo user = users.get(userIndex);
            
            indicator.setText(String.format("Processing files for user %s (%d/%d)...", 
                            user.username, userIndex + 1, users.size()));
            
            // 计算当前用户处理的文件数量
            int filesToProcess = filesPerUser;
            if (userIndex == users.size() - 1) {
                // 最后一个用户处理剩余的所有文件
                filesToProcess = modifiedFiles.size() - currentFileIndex;
            }
            
            // 检出分配给当前用户的文件
            boolean hasValidFiles = false;
            for (int i = 0; i < filesToProcess && currentFileIndex < modifiedFiles.size(); i++) {
                String file = modifiedFiles.get(currentFileIndex++);
                try {
                    System.out.println("Checking out file: " + file + " for user: " + user.username);
                    checkoutFile(project, repository, commitHash, file);
                    hasValidFiles = true;
                } catch (Exception e) {
                    System.err.println("Failed to checkout file " + file + " for user " + user.username + ": " + e.getMessage());
                }
            }
            
            // 如果有文件被成功检出，则创建提交
            if (hasValidFiles) {
                System.out.println("Adding files to staging area for user: " + user.username);
                addAllFiles(project, repository);
                String newMessage = processCommitMessage(commitMessage, user.username);
                createCommit(project, repository, newMessage, user);
                
                // 立即推送该用户的commit（使用该用户的凭据）
                pushToRemote(project, repository, user, remoteConfig);
                
            } else {
                System.out.println("No valid files to commit for user: " + user.username);
            }
            
            indicator.setFraction(0.4 + 0.5 * (userIndex + 1) / users.size());
        }
    }
    
    @Override
    public boolean canExecute(List<String> modifiedFiles, int userCount) {
        return modifiedFiles.size() >= userCount;
    }
    
    @Override
    public String getStrategyName() {
        return "FILES";
    }
    
    /**
     * 重置到父commit
     */
    private void resetToParent(Project project, GitRepository repository, String parentCommit) throws Exception {
        System.out.println("Resetting to parent commit: " + parentCommit);
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.RESET);
        handler.addParameters("--hard", parentCommit);
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new RuntimeException("Failed to reset to parent commit: " + 
                                     String.join("\n", result.getErrorOutput()));
        }
        System.out.println("Successfully reset to parent commit: " + parentCommit);
    }
}