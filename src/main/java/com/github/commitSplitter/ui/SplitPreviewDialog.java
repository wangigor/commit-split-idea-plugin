package com.github.commitSplitter.ui;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.github.commitSplitter.utils.GitUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SplitPreviewDialog extends DialogWrapper {
    private final Project project;
    private final GitRepository repository;
    private final String commitHash;
    private final CommitSplitterSettings settings;
    private JTable previewTable;
    private JLabel infoLabel;
    
    public SplitPreviewDialog(Project project, GitRepository repository, 
                              String commitHash, CommitSplitterSettings settings) {
        super(project);
        this.project = project;
        this.repository = repository;
        this.commitHash = commitHash;
        this.settings = settings;
        
        setTitle("Split Commit Preview");
        setSize(700, 500);
        init();
        loadPreview();
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 信息标签
        infoLabel = new JLabel();
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(infoLabel, BorderLayout.NORTH);
        
        // 预览表格
        previewTable = new JTable();
        JBScrollPane scrollPane = new JBScrollPane(previewTable);
        scrollPane.setPreferredSize(new Dimension(650, 350));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 说明文本
        JTextArea helpText = new JTextArea(
                "This preview shows how the commit will be split among users.\\n" +
                "Each row represents a new commit that will be created.\\n" +
                "The original commit will be replaced by these new commits."
        );
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(helpText, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void loadPreview() {
        try {
            System.out.println("开始同步加载commit信息: " + commitHash);
            
            // 直接在EDT同步执行（忽略EDT警告，优先保证功能正常）
            String commitMessage = GitUtils.getCommitMessage(repository, commitHash);
            System.out.println("获取commit消息: " + commitMessage);
            
            List<String> modifiedFiles = GitUtils.getModifiedFiles(repository, commitHash);
            System.out.println("获取修改文件数量: " + modifiedFiles.size());
            
            // 计算拆分策略（可能抛出异常）
            CommitSplitterSettings.SplitStrategy strategy = determineStrategy(modifiedFiles);
            System.out.println("确定拆分策略: " + strategy);
            
            // 获取详细的hunk信息用于显示
            int totalHunks = getTotalHunkCount(modifiedFiles);
            
            // 构建详细的文件信息
            StringBuilder fileDetails = new StringBuilder();
            for (String file : modifiedFiles) {
                try {
                    int fileHunks = GitUtils.getHunkCountUsingGitCli(project, repository, commitHash, file);
                    // 获取自定义hunks的详细信息
                    List<GitUtils.CustomHunk> customHunks = GitUtils.getCustomHunksUsingGitCli(project, repository, commitHash, file);
                    
                    fileDetails.append(String.format("<br>&nbsp;&nbsp;• %s (%d hunks)", file, fileHunks));
                    
                    // 显示每个hunk的类型
                    for (int i = 0; i < customHunks.size(); i++) {
                        GitUtils.CustomHunk hunk = customHunks.get(i);
                        fileDetails.append(String.format("<br>&nbsp;&nbsp;&nbsp;&nbsp;- Hunk %d: %s", 
                            i + 1, hunk.getDescription()));
                    }
                } catch (Exception e) {
                    fileDetails.append(String.format("<br>&nbsp;&nbsp;• %s (error: %s)", file, e.getMessage()));
                }
            }
            
            // 直接更新UI
            infoLabel.setText(String.format(
                    "<html><b>Commit:</b> %s<br>" +
                    "<b>Message:</b> %s<br>" +
                    "<b>Modified Files:</b> %d<br>" +
                    "<b>Total Hunks:</b> %d<br>" +
                    "<b>Strategy:</b> %s<br>" +
                    "<b>Users:</b> %d<br>" +
                    "<b>File Details:</b>%s</html>",
                    commitHash.substring(0, 8),
                    commitMessage,
                    modifiedFiles.size(),
                    totalHunks,
                    strategy,
                    settings.users.size(),
                    fileDetails.toString()
            ));
            
            // 创建预览数据
            PreviewTableModel model = new PreviewTableModel(strategy, modifiedFiles);
            previewTable.setModel(model);
            System.out.println("UI更新完成");
            
        } catch (RuntimeException e) {
            System.out.println("RuntimeException: " + e.getMessage());
            e.printStackTrace();
            // 资源不足的情况
            infoLabel.setText("<html><font color='red'>" + e.getMessage() + "</font></html>");
            previewTable.setModel(new PreviewTableModel(null, null));
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            infoLabel.setText("<html><font color='red'>Error loading commit information: " + 
                             e.getMessage() + "</font></html>");
            previewTable.setModel(new PreviewTableModel(null, null));
        }
    }
    
    private CommitSplitterSettings.SplitStrategy determineStrategy(List<String> modifiedFiles) throws Exception {
        if (settings.defaultStrategy != CommitSplitterSettings.SplitStrategy.AUTO) {
            return settings.defaultStrategy;
        }
        
        int fileCount = modifiedFiles.size();
        int userCount = settings.users.size();
        
        if (fileCount >= userCount) {
            return CommitSplitterSettings.SplitStrategy.FILES;
        }
        
        // 实际计算hunks数量，和执行逻辑保持一致
        int totalHunks = getTotalHunkCount(modifiedFiles);
        if (totalHunks >= userCount) {
            return CommitSplitterSettings.SplitStrategy.HUNKS;
        }
        
        // 资源不足，抛出异常
        throw new RuntimeException(String.format(
            "Cannot split commit: only %d files and %d hunks available for %d users. " +
            "Need at least %d files or %d hunks to split effectively.",
            fileCount, totalHunks, userCount, userCount, userCount));
    }
    
    private int getTotalHunkCount(List<String> modifiedFiles) throws Exception {
        int totalHunks = 0;
        System.out.println("=== PreviewDialog Hunk Analysis ===");
        
        for (String file : modifiedFiles) {
            try {
                System.out.println(String.format("PreviewDialog analyzing file: %s", file));
                int hunkCount = GitUtils.getHunkCountUsingGitCli(project, repository, commitHash, file);
                System.out.println(String.format("PreviewDialog: File %s has %d hunks", file, hunkCount));
                totalHunks += hunkCount;
                
                if (hunkCount == 0) {
                    System.out.println(String.format("PreviewDialog警告: 文件 %s 没有检测到hunk", file));
                }
            } catch (Exception e) {
                System.out.println(String.format("PreviewDialog Error getting hunks for file %s: %s", file, e.getMessage()));
                e.printStackTrace();
            }
        }
        
        System.out.println(String.format("PreviewDialog total hunks: %d", totalHunks));
        System.out.println("=== End PreviewDialog Hunk Analysis ===");
        return totalHunks;
    }
    
    private class PreviewTableModel extends AbstractTableModel {
        private final String[] columnNames = {"#", "User", "Description", "Strategy"};
        private final CommitSplitterSettings.SplitStrategy strategy;
        private final List<String> modifiedFiles;
        
        public PreviewTableModel(CommitSplitterSettings.SplitStrategy strategy, List<String> modifiedFiles) {
            this.strategy = strategy;
            this.modifiedFiles = modifiedFiles;
        }
        
        @Override
        public int getRowCount() {
            return strategy == null ? 0 : settings.users.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CommitSplitterSettings.UserInfo user = settings.users.get(rowIndex);
            
            switch (columnIndex) {
                case 0:
                    return rowIndex + 1;
                case 1:
                    return user.toString();
                case 2:
                    return getDescriptionForUser(rowIndex);
                case 3:
                    return strategy.toString();
                default:
                    return "";
            }
        }
        
        private String getDescriptionForUser(int userIndex) {
            switch (strategy) {
                case FILES:
                    int filesPerUser = Math.max(1, modifiedFiles.size() / settings.users.size());
                    int startFile = userIndex * filesPerUser;
                    int endFile = Math.min(startFile + filesPerUser, modifiedFiles.size());
                    if (userIndex == settings.users.size() - 1) {
                        endFile = modifiedFiles.size(); // 最后一个用户处理剩余文件
                    }
                    return String.format("Files %d-%d (%d files)", startFile + 1, endFile, endFile - startFile);
                    
                case HUNKS:
                    return getHunkDescriptionForUser(userIndex);

                default:
                    return "Auto-determined changes";
            }
        }
        
        private String getHunkDescriptionForUser(int userIndex) {
            // 简化预览，避免在EDT上执行Git操作
            // 实际的hunk分析会在执行时进行
            return String.format("Hunks assigned to user %d (details calculated during execution)", userIndex + 1);
        }
    }
}