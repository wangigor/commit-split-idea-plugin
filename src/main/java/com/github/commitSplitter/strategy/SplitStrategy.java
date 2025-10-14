package com.github.commitSplitter.strategy;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.services.RemoteConfig;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import git4idea.repo.GitRepository;

import java.util.List;
import java.util.Map;

public interface SplitStrategy {
    /**
     * 执行commit拆分
     * @param project IntelliJ项目
     * @param repository Git仓库
     * @param commitHash 要拆分的commit hash
     * @param commitMessage 原始commit消息
     * @param modifiedFiles 修改的文件列表
     * @param users 用户列表
     * @param indicator 进度指示器
     * @param remoteConfig 远程仓库配置（可为null，表示不推送）
     * @throws Exception 拆分失败时抛出异常
     */
    void execute(Project project, GitRepository repository, String commitHash, 
                String commitMessage, List<String> modifiedFiles, 
                List<CommitSplitterSettings.UserInfo> users, ProgressIndicator indicator,
                RemoteConfig remoteConfig, Map<String, String> userPrefixes) throws Exception;
    
    /**
     * 检查是否能够执行该策略
     * @param modifiedFiles 修改的文件列表
     * @param userCount 用户数量
     * @return 是否可以执行
     */
    boolean canExecute(List<String> modifiedFiles, int userCount);
    
    /**
     * 获取策略名称
     */
    String getStrategyName();
}
