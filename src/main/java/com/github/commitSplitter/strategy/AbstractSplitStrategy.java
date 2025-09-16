package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.intellij.openapi.project.Project;
import git4idea.commands.*;
import git4idea.repo.GitRepository;

import java.io.File;
import java.net.URLEncoder;

public abstract class AbstractSplitStrategy implements SplitStrategy {
    
    protected final git4idea.commands.Git git = git4idea.commands.Git.getInstance();
    
    /**
     * 检出指定commit的文件
     */
    protected void checkoutFile(Project project, GitRepository repository, String commitHash, String filePath) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.CHECKOUT);
        handler.addParameters(commitHash, "--", filePath);
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new RuntimeException("Failed to checkout file " + filePath + ": " + 
                                     String.join("\n", result.getErrorOutput()));
        }
    }
    
    /**
     * 添加所有文件到暂存区
     */
    protected void addAllFiles(Project project, GitRepository repository) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.ADD);
        handler.addParameters(".");
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new RuntimeException("Failed to add files: " + String.join("\n", result.getErrorOutput()));
        }
    }
    
    /**
     * 创建commit
     */
    protected void createCommit(Project project, GitRepository repository, String message, 
                              CommitSplitterSettings.UserInfo user) throws Exception {
        
        System.out.println("Creating commit for user: " + user.username + " with message: " + message);
        
        // 先检查是否有文件需要提交
        GitLineHandler statusHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.STATUS);
        statusHandler.addParameters("--porcelain");
        GitCommandResult statusResult = git.runCommand(statusHandler);
        
        System.out.println("Git status check - Success: " + statusResult.success());
        System.out.println("Git status output: " + String.join("\n", statusResult.getOutput()));
        
        if (statusResult.success() && statusResult.getOutput().isEmpty()) {
            System.out.println("No changes to commit for user: " + user.username);
            return; // 没有变更，跳过提交
        }
        
        System.out.println("Proceeding with commit creation for user: " + user.username);
        
        // 创建Git命令，使用-c参数设置临时配置
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.CONFIG);
        handler.addParameters("user.name", user.username);
        
        // 先临时设置用户名
        GitCommandResult nameResult = git.runCommand(handler);
        
        // 再设置邮箱
        GitLineHandler emailHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.CONFIG);
        emailHandler.addParameters("user.email", user.email);
        GitCommandResult emailResult = git.runCommand(emailHandler);
        
        try {
            // 执行commit
            GitLineHandler commitHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.COMMIT);
            commitHandler.addParameters("-m", message);
            commitHandler.addParameters("--author", user.username + " <" + user.email + ">");
            
            System.out.println("Executing git commit command with author and committer: " + user.username + " <" + user.email + ">");
            
            GitCommandResult result = git.runCommand(commitHandler);
            
            System.out.println("Git commit result - Success: " + result.success());
            System.out.println("Git commit stdout: " + String.join("\n", result.getOutput()));
            System.out.println("Git commit stderr: " + String.join("\n", result.getErrorOutput()));
            
            if (!result.success()) {
                String errorOutput = String.join("\n", result.getErrorOutput());
                System.err.println("Git commit failed for user: " + user.username);
                System.err.println("Git commit error: " + errorOutput);
                throw new RuntimeException("Failed to create commit for user " + user.username + ": " + errorOutput);
            }
            
        } finally {
            // 重要：恢复原始的Git配置（如果需要的话）
            // 这里我们不恢复，因为每次commit都会重新设置
        }
        
        System.out.println("Successfully created commit for user: " + user.username);
    }
    
    /**
     * 推送到远程仓库
     */
    protected void pushToRemote(Project project, GitRepository repository, 
                              CommitSplitterSettings.UserInfo user, RemoteConfig remoteConfig) throws Exception {
        String password = user.getPassword();
        if (password == null || password.trim().isEmpty()) {
            System.out.println("No password configured for user " + user.username + ", skipping push");
            return; // 没有密码则跳过推送
        }
        
        if (remoteConfig == null) {
            System.out.println("No remote config provided for user " + user.username + ", skipping push");
            return; // 没有远程配置则跳过推送
        }
        
        try {
            System.out.println("Starting push to remote for user: " + user.username);
            
            // 使用 RemoteConfig 中的配置
            String remoteName = remoteConfig.getRemoteName();
            String targetBranch = remoteConfig.getBranchName();
            
            System.out.println("Target remote: " + remoteName + ", branch: " + targetBranch);
            
            // 检查是否有远程仓库配置
            String remoteUrl = getRemoteUrlForRemote(project, repository, remoteName);
            if (remoteUrl == null || remoteUrl.trim().isEmpty()) {
                throw new Exception("Remote '" + remoteName + "' not found or not configured");
            }
            
            System.out.println("Remote URL: " + remoteUrl);
            
            // 构建认证URL（如果需要）
            String authUrl = buildAuthenticatedUrl(remoteUrl, user.username, password);
            
            // 执行推送
            System.out.println("Executing git push command...");
            
            GitCommandResult result = null;
            
            // 如果需要认证，使用临时设置远程URL的方法
            if (!authUrl.equals(remoteUrl)) {
                // 方法1: 临时修改远程URL进行推送
                result = pushWithTemporaryRemoteUrl(project, repository, authUrl, targetBranch, remoteName);
            } else {
                // 方法2: 使用指定的远程推送
                result = pushToRemote(project, repository, remoteName, targetBranch);
            }
            
            System.out.println("Push result - Success: " + result.success());
            System.out.println("Push stdout: " + String.join("\n", result.getOutput()));
            
            if (!result.success()) {
                String errorOutput = String.join("\n", result.getErrorOutput());
                System.err.println("Push failed for user " + user.username);
                System.err.println("Push error: " + errorOutput);
                
                // 提供更友好的错误信息
                if (errorOutput.contains("Authentication failed") || errorOutput.contains("401") || errorOutput.contains("403")) {
                    throw new Exception("Authentication failed. Please check username and password/token for user: " + user.username);
                } else if (errorOutput.contains("Could not resolve host") || errorOutput.contains("Connection refused")) {
                    throw new Exception("Network error: Unable to connect to remote repository");
                } else if (errorOutput.contains("Permission denied")) {
                    throw new Exception("Permission denied. Check repository access rights for user: " + user.username);
                } else {
                    throw new Exception("Push failed: " + (errorOutput.isEmpty() ? "Unknown error" : errorOutput));
                }
            }
            
            System.out.println("Successfully pushed commits for user: " + user.username);
            
        } catch (Exception e) {
            System.err.println("Push operation failed for user " + user.username + ": " + e.getMessage());
            throw new Exception("Failed to push commits for user " + user.username + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取当前分支名
     */
    protected String getCurrentBranch(Project project, GitRepository repository) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REV_PARSE);
        handler.addParameters("--abbrev-ref", "HEAD");
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new Exception("Failed to get current branch: " + String.join("\n", result.getErrorOutput()));
        }
        
        return result.getOutput().isEmpty() ? null : result.getOutput().get(0).trim();
    }
    
    /**
     * 获取远程仓库URL (默认获取origin)
     */
    protected String getRemoteUrl(Project project, GitRepository repository) throws Exception {
        return getRemoteUrlForRemote(project, repository, "origin");
    }
    
    /**
     * 获取指定远程仓库的URL
     */
    protected String getRemoteUrlForRemote(Project project, GitRepository repository, String remoteName) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REMOTE);
        handler.addParameters("get-url", remoteName);
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new Exception("Failed to get URL for remote '" + remoteName + "': " + String.join("\n", result.getErrorOutput()));
        }
        
        return result.getOutput().isEmpty() ? null : result.getOutput().get(0).trim();
    }
    
    /**
     * 构建包含认证信息的URL
     */
    protected String buildAuthenticatedUrl(String originalUrl, String username, String password) {
        if (originalUrl == null || username == null || password == null) {
            return originalUrl;
        }
        
        try {
            String cleanUrl = originalUrl;
            String protocol = "";
            
            // 确定协议并移除现有的认证信息（如果有）
            if (originalUrl.startsWith("https://")) {
                protocol = "https://";
                cleanUrl = originalUrl.substring(8); // 移除 "https://"
                
                // 如果URL包含认证信息，移除它
                if (cleanUrl.contains("@")) {
                    int atIndex = cleanUrl.indexOf("@");
                    cleanUrl = cleanUrl.substring(atIndex + 1); // 移除 "user:pass@" 部分
                }
            } else if (originalUrl.startsWith("http://")) {
                protocol = "http://";
                cleanUrl = originalUrl.substring(7); // 移除 "http://"
                
                // 如果URL包含认证信息，移除它
                if (cleanUrl.contains("@")) {
                    int atIndex = cleanUrl.indexOf("@");
                    cleanUrl = cleanUrl.substring(atIndex + 1); // 移除 "user:pass@" 部分
                }
            } else {
                // 对于SSH URL或其他格式，返回原始URL（需要其他认证方式）
                return originalUrl;
            }
            
            // 构建新的认证URL
            String encodedUsername = java.net.URLEncoder.encode(username, "UTF-8");
            String encodedPassword = java.net.URLEncoder.encode(password, "UTF-8");
            
            String newUrl = protocol + encodedUsername + ":" + encodedPassword + "@" + cleanUrl;
            
            System.out.println("Built authenticated URL for user " + username + ": " + protocol + username + ":***@" + cleanUrl);
            
            return newUrl;
            
        } catch (Exception e) {
            System.err.println("Failed to build authenticated URL: " + e.getMessage());
            return originalUrl;
        }
    }
    
    /**
     * 使用临时远程URL进行推送
     */
    private GitCommandResult pushWithTemporaryRemoteUrl(Project project, GitRepository repository, 
                                                       String authUrl, String branch, String remoteName) throws Exception {
        // 获取当前的远程URL
        String originalUrl = getRemoteUrlForRemote(project, repository, remoteName);
        
        try {
            // 临时设置远程URL为包含认证信息的URL
            GitLineHandler setUrlHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REMOTE);
            setUrlHandler.addParameters("set-url", remoteName, authUrl);
            
            GitCommandResult setUrlResult = git.runCommand(setUrlHandler);
            if (!setUrlResult.success()) {
                throw new Exception("Failed to set temporary remote URL: " + String.join("\n", setUrlResult.getErrorOutput()));
            }
            
            // 执行推送
            GitLineHandler pushHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.PUSH);
            pushHandler.addParameters(remoteName, branch);
            
            return git.runCommand(pushHandler);
            
        } finally {
            // 恢复原始的远程URL
            if (originalUrl != null && !originalUrl.isEmpty()) {
                try {
                    GitLineHandler restoreUrlHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REMOTE);
                    restoreUrlHandler.addParameters("set-url", remoteName, originalUrl);
                    git.runCommand(restoreUrlHandler);
                } catch (Exception e) {
                    System.err.println("Warning: Failed to restore original remote URL: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 推送到指定远程仓库
     */
    private GitCommandResult pushToRemote(Project project, GitRepository repository, String remoteName, String branch) {
        GitLineHandler pushHandler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.PUSH);
        pushHandler.addParameters(remoteName, branch);
        
        return git.runCommand(pushHandler);
    }
    
    
    /**
     * 处理commit消息，添加用户前缀
     */
    protected String processCommitMessage(String originalMessage, String username) {
        if (originalMessage == null) {
            originalMessage = "";
        }
        
        // 检查是否已经有该用户的前缀
        String expectedPrefix = "@" + username + " ";
        if (originalMessage.startsWith(expectedPrefix)) {
            System.out.println("Commit message already has correct user prefix for " + username + ": " + originalMessage);
            return originalMessage; // 已经有正确的用户前缀
        }
        
        // 如果有其他用户的前缀，替换为当前用户
        if (originalMessage.startsWith("@")) {
            // 找到第一个空格，替换用户名
            int spaceIndex = originalMessage.indexOf(" ");
            if (spaceIndex > 0) {
                String restOfMessage = originalMessage.substring(spaceIndex + 1);
                String newMessage = "@" + username + " " + restOfMessage;
                System.out.println("Replaced existing user prefix with " + username + ": '" + newMessage + "'");
                return newMessage;
            }
        }
        
        // 没有前缀，添加用户前缀
        String newMessage = "@" + username + " " + originalMessage;
        System.out.println("Added user prefix for " + username + ": '" + newMessage + "'");
        return newMessage;
    }
}