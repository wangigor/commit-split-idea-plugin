package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.utils.GitUtils;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;

import java.util.List;

public class SplitStrategyFactory {
    
    /**
     * 根据设置和commit信息创建合适的拆分策略
     */
    public static SplitStrategy createStrategy(CommitSplitterSettings settings, List<String> modifiedFiles, 
                                             String commitHash, Project project, GitRepository repository) throws Exception {
        
        try {
            if (settings.defaultStrategy != CommitSplitterSettings.SplitStrategy.AUTO) {
                return createStrategyByType(settings.defaultStrategy);
            }
            
            // 自动选择策略
            int fileCount = modifiedFiles.size();
            int userCount = settings.users.size();
            
            System.out.println("Strategy selection: " + fileCount + " files, " + userCount + " users");
            
            // 优先按文件拆分
            FileBasedSplitStrategy fileStrategy = new FileBasedSplitStrategy();
            if (fileStrategy.canExecute(modifiedFiles, userCount)) {
                System.out.println("Selected FILES strategy");
                return fileStrategy;
            }
            
            // 如果文件数不够，检查hunks数量
            int totalHunks = getTotalHunkCount(project, repository, commitHash, modifiedFiles);
            System.out.println("Total hunks found: " + totalHunks);
            
            if (totalHunks >= userCount) {
                System.out.println("Selected HUNKS strategy");
                return new HunkBasedSplitStrategy();
            }
            
            // 资源不足，无法拆分
            throw new RuntimeException(String.format(
                "Cannot split commit: only %d files and %d hunks available for %d users.",
                fileCount, totalHunks, userCount));
                
        } catch (Exception e) {
            System.err.println("Failed to create strategy: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create split strategy: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据策略类型创建策略实例
     */
    public static SplitStrategy createStrategyByType(CommitSplitterSettings.SplitStrategy strategyType) {
        switch (strategyType) {
            case FILES:
                return new FileBasedSplitStrategy();
            case HUNKS:
                return new HunkBasedSplitStrategy();
            default:
                throw new IllegalArgumentException("Unsupported strategy: " + strategyType);
        }
    }
    
    /**
     * 计算总的hunk数量
     */
    private static int getTotalHunkCount(Project project, GitRepository repository, 
                                       String commitHash, List<String> modifiedFiles) throws Exception {
        int totalHunks = 0;
        
        for (String file : modifiedFiles) {
            try {
                int hunkCount = GitUtils.getHunkCountUsingGitCli(project, repository, commitHash, file);
                totalHunks += hunkCount;
            } catch (Exception e) {
                // 忽略单个文件的错误，继续处理其他文件
            }
        }
        
        return totalHunks;
    }
}