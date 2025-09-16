package com.github.commitSplitter.utils;

import git4idea.commands.*;
import git4idea.repo.GitRepository;
import com.intellij.openapi.project.Project;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitUtils {
    
    public static String getCommitMessage(GitRepository gitRepository, String commitHash) throws Exception {
        try (Repository repository = openJGitRepository(gitRepository)) {
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("Commit not found: " + commitHash);
            }
            
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                return commit.getShortMessage();
            }
        }
    }
    
    public static List<String> getModifiedFiles(GitRepository gitRepository, String commitHash) throws Exception {
        List<String> modifiedFiles = new ArrayList<>();
        
        try (Repository repository = openJGitRepository(gitRepository)) {
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("Commit not found: " + commitHash);
            }
            
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                RevCommit parent = commit.getParent(0);
                revWalk.parseCommit(parent);
                
                try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    diffFormatter.setRepository(repository);
                    List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
                    
                    for (DiffEntry diff : diffs) {
                        String filePath = diff.getNewPath();
                        if (DiffEntry.DEV_NULL.equals(filePath)) {
                            filePath = diff.getOldPath();
                        }
                        modifiedFiles.add(filePath);
                    }
                }
            }
        }
        
        return modifiedFiles;
    }
    
    
    
    public static boolean isWorkingTreeClean(GitRepository gitRepository) throws Exception {
        try (Repository repository = openJGitRepository(gitRepository);
             Git git = new Git(repository)) {
            
            return git.status().call().isClean();
        }
    }
    
    public static String getParentCommitHash(GitRepository gitRepository, String commitHash) throws Exception {
        try (Repository repository = openJGitRepository(gitRepository)) {
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("Commit not found: " + commitHash);
            }
            
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                if (commit.getParentCount() > 0) {
                    return commit.getParent(0).getName();
                }
                return null;
            }
        }
    }
    
    private static Repository openJGitRepository(GitRepository gitRepository) throws IOException {
        File gitDir = new File(gitRepository.getRoot().getPath(), ".git");
        return new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
    }
    
    
    public static int getHunkCountUsingGitCli(Project project, GitRepository gitRepository, String commitHash, String filePath) throws Exception {
        git4idea.commands.Git git = git4idea.commands.Git.getInstance();
        
        // 方法1: 尝试 git diff parent commit
        GitCommandResult result = null;
        String method = "";
        
        try {
            // 首先尝试使用git diff，添加更多参数确保完整输出
            GitLineHandler diffHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.DIFF);
            diffHandler.addParameters("--no-prefix", "--unified=3", commitHash + "^", commitHash, "--", filePath);
            
            result = git.runCommand(diffHandler);
            if (result.success()) {
                method = "git diff";
            }
        } catch (Exception e) {
            // git diff failed, try alternatives
        }
        
        // 方法2: 如果git diff失败，尝试git show
        if (result == null || !result.success()) {
            try {
                GitLineHandler showHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.SHOW);
                showHandler.addParameters("--patch", "--format=", commitHash, "--", filePath);
                
                result = git.runCommand(showHandler);
                if (result.success()) {
                    method = "git show --patch";
                }
            } catch (Exception e) {
                // git show --patch failed
            }
        }
        
        // 方法3: 如果都失败，尝试更基础的git show
        if (result == null || !result.success()) {
            try {
                GitLineHandler basicShowHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.SHOW);
                basicShowHandler.addParameters(commitHash, "--", filePath);
                
                result = git.runCommand(basicShowHandler);
                if (result.success()) {
                    method = "git show (basic)";
                } else {
                    throw new RuntimeException("All git commands failed for file " + filePath + ": " + 
                                             String.join("\\n", result.getErrorOutput()));
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to get diff for file " + filePath + " using all methods: " + e.getMessage(), e);
            }
        }
        
        // 直接使用自定义算法计算hunk数量
        int hunkCount = getCustomHunkCount(result.getOutput(), filePath);
        
        return hunkCount;
    }


    // Git标准hunk解析方法 - 遵循@@标记的hunk边界
    public static List<CustomHunk> parseCustomHunks(List<String> diffLines, String filePath) {
        List<CustomHunk> hunks = new ArrayList<>();
        List<String> patchHeaders = new ArrayList<>();
        List<String> currentHunkLines = new ArrayList<>();
        boolean inHunk = false;
        int hunkCounter = 1;
        
        for (String line : diffLines) {
            // 收集完整的patch头部信息
            if (line.startsWith("diff --git") || line.startsWith("index ") || 
                line.startsWith("---") || line.startsWith("+++")) {
                patchHeaders.add(line);
                continue;
            }
            
            // @@行标记hunk的开始
            if (line.startsWith("@@")) {
                // 如果已经在处理一个hunk，先保存它
                if (inHunk && !currentHunkLines.isEmpty()) {
                    saveCompleteHunk(hunks, patchHeaders, currentHunkLines, filePath, hunkCounter++);
                    currentHunkLines = new ArrayList<>();
                }
                
                // 开始新的hunk
                currentHunkLines.add(line);
                inHunk = true;
                
            } else if (inHunk) {
                // 在hunk内部，收集所有行（+，-，上下文行）
                currentHunkLines.add(line);
            }
        }
        
        // 保存最后一个hunk
        if (inHunk && !currentHunkLines.isEmpty()) {
            saveCompleteHunk(hunks, patchHeaders, currentHunkLines, filePath, hunkCounter);
        }
        
        return hunks;
    }
    
    // 保存完整的Git hunk（包含原始@@头部）
    private static void saveCompleteHunk(List<CustomHunk> hunks, List<String> patchHeaders, 
                                       List<String> hunkLines, String filePath, int hunkId) {
        if (hunkLines.isEmpty()) return;
        
        List<String> completePatch = new ArrayList<>();
        
        // 添加patch头部信息
        if (patchHeaders.isEmpty() || patchHeaders.size() < 2) {
            // 如果没有完整的头部，生成标准的patch头部
            completePatch.add("diff --git a/" + filePath + " b/" + filePath);
            completePatch.add("index 0000000..0000000 100644");
            completePatch.add("--- a/" + filePath);
            completePatch.add("+++ b/" + filePath);
        } else {
            completePatch.addAll(patchHeaders);
        }
        
        // 添加完整的hunk内容（包括@@头部和所有变更行）
        completePatch.addAll(hunkLines);
        
        // 确定hunk类型
        HunkType hunkType = determineHunkType(hunkLines);
        
        CustomHunk hunk = new CustomHunk(hunkType, completePatch, filePath, hunkId);
        hunks.add(hunk);
    }
    
    // 根据hunk内容确定类型
    private static HunkType determineHunkType(List<String> hunkLines) {
        boolean hasAdditions = false;
        boolean hasDeletions = false;
        
        for (String line : hunkLines) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                hasAdditions = true;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                hasDeletions = true;
            }
        }
        
        if (hasAdditions && hasDeletions) {
            return HunkType.MODIFICATION; // 既有添加又有删除
        } else if (hasAdditions) {
            return HunkType.ADDITION;
        } else if (hasDeletions) {
            return HunkType.DELETION;
        } else {
            return HunkType.ADDITION; // 默认为添加类型
        }
    }
    

    
    
    // 基于自定义解析计算hunk数量的方法
    public static int getCustomHunkCount(List<String> diffLines, String filePath) {
        List<CustomHunk> hunks = parseCustomHunks(diffLines, filePath);
        return hunks.size();
    }
    
    // 新的方法：获取自定义格式的hunks
    public static List<CustomHunk> getCustomHunksUsingGitCli(Project project, GitRepository gitRepository, String commitHash, String filePath) throws Exception {
        git4idea.commands.Git git = git4idea.commands.Git.getInstance();
        
        // 使用与getHunkCountUsingGitCli相同的Git命令逻辑
        GitCommandResult result = null;
        String method = "";
        
        try {
            // 方法1: 尝试 git diff
            GitLineHandler diffHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.DIFF);
            diffHandler.addParameters("--no-prefix", "--unified=3", commitHash + "^", commitHash, "--", filePath);
            
            result = git.runCommand(diffHandler);
            if (result.success()) {
                method = "git diff";
            }
        } catch (Exception e) {
            // git diff failed for custom hunks extraction
        }
        
        // 方法2: 如果git diff失败，尝试git show
        if (result == null || !result.success()) {
            try {
                GitLineHandler showHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.SHOW);
                showHandler.addParameters("--patch", "--format=", commitHash, "--", filePath);
                
                result = git.runCommand(showHandler);
                if (result.success()) {
                    method = "git show --patch";
                }
            } catch (Exception e) {
                // git show --patch failed for custom hunks
            }
        }
        
        // 方法3: 如果都失败，尝试更基础的git show
        if (result == null || !result.success()) {
            try {
                GitLineHandler basicShowHandler = new GitLineHandler(project, new File(gitRepository.getRoot().getPath()), GitCommand.SHOW);
                basicShowHandler.addParameters(commitHash, "--", filePath);
                
                result = git.runCommand(basicShowHandler);
                if (result.success()) {
                    method = "git show (basic)";
                } else {
                    throw new RuntimeException("All git commands failed for custom hunks extraction from file " + filePath);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to extract custom hunks from file " + filePath + " using all methods: " + e.getMessage(), e);
            }
        }
        
        // Successfully got custom hunks
        
        // 使用自定义解析获取hunks
        List<CustomHunk> hunks = parseCustomHunks(result.getOutput(), filePath);
        
        // 设置commit hash
        for (CustomHunk hunk : hunks) {
            hunk.setCommitHash(commitHash);
        }
        
        return hunks;
    }


    // Hunk类型枚举 - 支持Git的三种基本操作
    public enum HunkType {
        ADDITION("Addition", "纯添加"),
        DELETION("Deletion", "纯删除"),
        MODIFICATION("Modification", "修改");
        
        private final String displayName;
        private final String chineseName;
        
        HunkType(String displayName, String chineseName) {
            this.displayName = displayName;
            this.chineseName = chineseName;
        }
        
        public String getDisplayName() { return displayName; }
        public String getChineseName() { return chineseName; }
    }
    
    // 增强的Hunk数据结构
    public static class CustomHunk {
        private final HunkType type;
        private final List<String> hunkLines;
        private final String filePath;
        private final int startLineNumber;
        private final int lineCount;
        private String commitHash;
        
        public CustomHunk(HunkType type, List<String> hunkLines, String filePath, int startLineNumber) {
            this.type = type;
            this.hunkLines = new ArrayList<>(hunkLines);
            this.filePath = filePath;
            this.startLineNumber = startLineNumber;
            this.lineCount = hunkLines.size();
        }
        
        public HunkType getType() { return type; }
        public List<String> getHunkLines() { return hunkLines; }
        public String getFilePath() { return filePath; }
        public int getStartLineNumber() { return startLineNumber; }
        public int getLineCount() { return lineCount; }
        public String getCommitHash() { return commitHash; }
        
        public void setCommitHash(String commitHash) {
            this.commitHash = commitHash;
        }
        
        public String getDescription() {
            return String.format("%s (%d lines) at line %d", 
                type.getChineseName(), lineCount, startLineNumber);
        }
        
        // 生成完整的patch格式用于应用（现在直接返回已构建的完整patch）
        public List<String> generatePatchFormat() {
            // 现在hunkLines已经包含了完整的patch格式（包括headers和@@行）
            return new ArrayList<>(hunkLines);
        }
    }

    public static class HunkData {
        private final List<String> hunkLines;
        private final String filePath;
        private String commitHash;
        
        public HunkData(List<String> hunkLines, String filePath) {
            this.hunkLines = hunkLines;
            this.filePath = filePath;
        }
        
        public List<String> getHunkLines() {
            return hunkLines;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getCommitHash() {
            return commitHash;
        }
        
        public void setCommitHash(String commitHash) {
            this.commitHash = commitHash;
        }
    }

    public static class HunkInfo {
        public final int oldStartLine;
        public final int oldLineCount;
        public final int newStartLine;
        public final int newLineCount;
        public final EditList editList;
        
        public HunkInfo(int oldStartLine, int oldLineCount, int newStartLine, 
                        int newLineCount, EditList editList) {
            this.oldStartLine = oldStartLine;
            this.oldLineCount = oldLineCount;
            this.newStartLine = newStartLine;
            this.newLineCount = newLineCount;
            this.editList = editList;
        }
        
        public int getChangeLineCount() {
            return editList.size() > 0 ? editList.stream().mapToInt(edit -> edit.getLengthA() + edit.getLengthB()).sum() : 0;
        }
    }
}