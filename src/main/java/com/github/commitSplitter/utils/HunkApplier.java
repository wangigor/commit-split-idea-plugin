package com.github.commitSplitter.utils;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.intellij.openapi.project.Project;
import git4idea.commands.*;
import git4idea.repo.GitRepository;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.List;

public class HunkApplier {
    
    private final Project project;
    private final GitRepository gitRepository;
    private final git4idea.commands.Git git = git4idea.commands.Git.getInstance();
    
    public HunkApplier(Project project, GitRepository gitRepository) {
        this.project = project;
        this.gitRepository = gitRepository;
    }
    
    /**
     * 应用单个hunk到工作区
     */
    public void applyHunk(GitUtils.CustomHunk hunk) throws Exception {
        // 生成patch内容
        List<String> patchLines = hunk.generatePatchFormat();
        String patchContent = String.join("\n", patchLines);
        
        // 检查文件是否存在（在重置后的工作区中）
        File targetFile = new File(gitRepository.getRoot().getPath(), hunk.getFilePath());
        boolean fileExists = targetFile.exists();
        
        // Check if file exists before applying patch
        
        if (!fileExists) {
            // 文件不存在，需要先创建文件结构
            createFileForHunk(hunk);
        }
        
        // 应用智能路径修正 - 确保patch路径与文件系统路径一致
        String correctedPatchContent = smartCorrectPatchPaths(patchContent, hunk.getFilePath());
        
        // 首先尝试使用JGit ApplyCommand应用patch
        try {
            applyPatchUsingJGit(correctedPatchContent, hunk.getFilePath());
            
        } catch (Exception jgitException) {
            // 如果JGit失败，尝试使用git命令行应用patch
            try {
                applyPatchUsingGitApply(correctedPatchContent, hunk.getFilePath());
                
            } catch (Exception gitException) {
                throw new Exception("Failed to apply hunk for " + hunk.getFilePath() + ": JGit error: " + 
                                    jgitException.getMessage() + ", Git apply error: " + gitException.getMessage(), jgitException);
            }
        }
    }
    
    /**
     * 使用Git命令行应用patch
     */
    private void applyPatchUsingJGit(String patchContent, String filePath) throws Exception {
        try (Repository repository = openJGitRepository()) {
            Git git = new Git(repository);
            
            // 使用JGit的ApplyCommand
            ApplyCommand applyCommand = git.apply();
            applyCommand.setPatch(new ByteArrayInputStream(patchContent.getBytes()));
            
            ApplyResult result = applyCommand.call();
            
        } catch (Exception e) {
            throw new Exception("Failed to apply hunk using JGit for " + filePath + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * 使用git apply命令应用patch (fallback方法)
     */
    private void applyPatchUsingGitApply(String patchContent, String filePath) throws Exception {
        // 创建临时patch文件
        java.io.File tempPatchFile = java.io.File.createTempFile("hunk-patch-", ".patch");
        try {
            java.nio.file.Files.write(tempPatchFile.toPath(), patchContent.getBytes());
            
            // 使用ProcessBuilder来执行git apply命令
            ProcessBuilder pb = new ProcessBuilder(
                "git", "apply", "--verbose", tempPatchFile.getAbsolutePath()
            );
            pb.directory(new File(gitRepository.getRoot().getPath()));
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            String outputStr = output.toString();
            
            
            if (exitCode != 0) {
                throw new Exception("Git apply failed with exit code " + exitCode + ": " + outputStr);
            }
            
        } finally {
            // 清理临时文件
            if (tempPatchFile.exists()) {
                tempPatchFile.delete();
            }
        }
    }
    
    /**
     * 修正patch中的文件路径
     */
    private String correctPatchPaths(String patchContent, String filePath) {
        // 找到可能的模块前缀（如 aitask-inter/）
        String[] pathParts = filePath.split("/");
        if (pathParts.length > 1 && pathParts[0].contains("-")) {
            // 很可能第一个部分是模块名，需要在patch中去掉
            String modulePrefix = pathParts[0] + "/";
            String pathWithoutModule = filePath.substring(modulePrefix.length());
            
            
            // 替换patch中的路径
            String correctedPatch = patchContent
                    .replace("--- " + filePath, "--- " + pathWithoutModule)
                    .replace("+++ " + filePath, "+++ " + pathWithoutModule);
            
            return correctedPatch;
        }
        
        return patchContent;
    }
    
    /**
     * 智能路径修正 - 处理复杂的路径匹配问题
     */
    private String smartCorrectPatchPaths(String patchContent, String filePath) {
        // Apply smart path correction
        
        // 文件实际存在的路径（相对于仓库根目录）
        String actualFilePath = filePath;  // aitask-inter/src/main/java/...
        
        // 修正所有路径引用
        String correctedPatch = patchContent;
        
        // 1. 修正 diff --git 行
        correctedPatch = correctedPatch.replaceAll(
            "diff --git a/" + Pattern.quote(actualFilePath) + " b/" + Pattern.quote(actualFilePath),
            "diff --git a/" + actualFilePath + " b/" + actualFilePath
        );
        
        // 2. 修正 --- 和 +++ 行
        correctedPatch = correctedPatch.replaceAll(
            "--- " + Pattern.quote(actualFilePath),
            "--- a/" + actualFilePath
        );
        correctedPatch = correctedPatch.replaceAll(
            "\\+\\+\\+ " + Pattern.quote(actualFilePath),
            "+++ b/" + actualFilePath
        );
        
        // 3. 如果还没有a/和b/前缀，添加它们
        if (!correctedPatch.contains("--- a/")) {
            correctedPatch = correctedPatch.replaceAll(
                "--- " + Pattern.quote(actualFilePath),
                "--- a/" + actualFilePath
            );
        }
        if (!correctedPatch.contains("+++ b/")) {
            correctedPatch = correctedPatch.replaceAll(
                "\\+\\+\\+ " + Pattern.quote(actualFilePath),
                "+++ b/" + actualFilePath
            );
        }
        
        return correctedPatch;
    }
    
    /**
     * 为hunk创建文件结构（当文件不存在时）
     */
    private void createFileForHunk(GitUtils.CustomHunk hunk) throws Exception {
        String filePath = hunk.getFilePath();
        
        // 创建目录结构（如果不存在）
        File targetFile = new File(gitRepository.getRoot().getPath(), filePath);
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        // 创建空文件
        if (!targetFile.exists()) {
            targetFile.createNewFile();
        }
    }
    
    
    /**
     * 添加文件到暂存区
     */
    public void addFiles() throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.ADD);
        handler.addParameters(".");
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new RuntimeException("Failed to add files: " + String.join("\n", result.getErrorOutput()));
        }
    }
    
    /**
     * 创建commit
     */
    public void createCommit(String message, CommitSplitterSettings.UserInfo user) throws Exception {
        // 先检查是否有文件需要提交
        GitLineHandler statusHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.STATUS);
        statusHandler.addParameters("--porcelain");
        GitCommandResult statusResult = git.runCommand(statusHandler);
        
        if (statusResult.success() && statusResult.getOutput().isEmpty()) {
            return; // 没有变更，跳过提交
        }
        
        // 创建Git命令，使用git config设置临时配置
        GitLineHandler nameHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.CONFIG);
        nameHandler.addParameters("user.name", user.username);
        
        // 先临时设置用户名
        GitCommandResult nameResult = git.runCommand(nameHandler);
        
        // 再设置邮箱
        GitLineHandler emailHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.CONFIG);
        emailHandler.addParameters("user.email", user.email);
        GitCommandResult emailResult = git.runCommand(emailHandler);
        
        try {
            // 执行commit
            GitLineHandler commitHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.COMMIT);
            commitHandler.addParameters("-m", message);
            commitHandler.addParameters("--author", user.username + " <" + user.email + ">");
            
            GitCommandResult result = git.runCommand(commitHandler);
            
            if (!result.success()) {
                String errorOutput = String.join("\n", result.getErrorOutput());
                System.err.println("Git commit failed for user: " + user.username + ": " + errorOutput);
                throw new RuntimeException("Failed to create commit for user " + user.username + ": " + errorOutput);
            }
            
        } finally {
            // 重要：恢复原始的Git配置（如果需要的话）
            // 这里我们不恢复，因为每次commit都会重新设置
        }
    }
    
    private Repository openJGitRepository() throws IOException {
        File gitDir = new File(gitRepository.getRoot().getPath(), ".git");
        return new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
    }
}