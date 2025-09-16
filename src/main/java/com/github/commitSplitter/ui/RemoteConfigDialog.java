package com.github.commitSplitter.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.util.ui.JBUI;
import git4idea.commands.*;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoteConfigDialog extends DialogWrapper {
    private JComboBox<String> remoteComboBox;
    private JComboBox<String> branchComboBox;
    private final Project project;
    private final GitRepository repository;
    private final git4idea.commands.Git git;
    
    public RemoteConfigDialog(Project project, GitRepository repository) {
        super(project);
        this.project = project;
        this.repository = repository;
        this.git = git4idea.commands.Git.getInstance();
        
        setTitle("Remote Repository Configuration");
        setOKButtonText("Continue Split");
        setCancelButtonText("Cancel");
        
        init();
        loadRemoteData();
    }
    
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Remote repository selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Remote Repository:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        remoteComboBox = new JComboBox<>();
        remoteComboBox.setPreferredSize(new Dimension(200, remoteComboBox.getPreferredSize().height));
        remoteComboBox.addActionListener(e -> loadBranchesForSelectedRemote());
        panel.add(remoteComboBox, gbc);
        
        // Branch selection
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Target Branch:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        branchComboBox = new JComboBox<>();
        branchComboBox.setPreferredSize(new Dimension(200, branchComboBox.getPreferredSize().height));
        branchComboBox.setEditable(true); // 允许用户输入新分支名
        panel.add(branchComboBox, gbc);
        
        // Help text
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel helpLabel = new JLabel("<html><i>Select the remote repository and target branch for pushing split commits.<br/>" +
                "You can type a new branch name if it doesn't exist yet.</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(helpLabel, gbc);
        
        return panel;
    }
    
    private void loadRemoteData() {
        SwingUtilities.invokeLater(() -> {
            try {
                // 加载远程仓库列表
                List<String> remotes = getAvailableRemotes();
                
                remoteComboBox.removeAllItems();
                for (String remote : remotes) {
                    remoteComboBox.addItem(remote);
                }
                
                // 默认选择 origin（如果存在）
                if (remotes.contains("origin")) {
                    remoteComboBox.setSelectedItem("origin");
                } else if (!remotes.isEmpty()) {
                    remoteComboBox.setSelectedIndex(0);
                }
                
                // 加载分支列表
                loadBranchesForSelectedRemote();
                
            } catch (Exception e) {
                e.printStackTrace();
                // 如果获取失败，至少提供基本选项
                remoteComboBox.addItem("origin");
                branchComboBox.addItem(getCurrentBranch());
            }
        });
    }
    
    private List<String> getAvailableRemotes() throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REMOTE);
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new Exception("Failed to get remotes: " + String.join("\n", result.getErrorOutput()));
        }
        
        List<String> remotes = new ArrayList<>();
        for (String line : result.getOutput()) {
            String remote = line.trim();
            if (!remote.isEmpty()) {
                remotes.add(remote);
            }
        }
        
        return remotes;
    }
    
    private void loadBranchesForSelectedRemote() {
        String selectedRemote = (String) remoteComboBox.getSelectedItem();
        if (selectedRemote == null || selectedRemote.trim().isEmpty()) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                branchComboBox.removeAllItems();
                
                // 获取当前分支
                String currentBranch = getCurrentBranch();
                if (currentBranch != null && !currentBranch.trim().isEmpty()) {
                    branchComboBox.addItem(currentBranch);
                }
                
                // 获取远程分支列表
                List<String> remoteBranches = getRemoteBranches(selectedRemote);
                for (String branch : remoteBranches) {
                    if (!branch.equals(currentBranch)) {
                        branchComboBox.addItem(branch);
                    }
                }
                
                // 默认选择当前分支
                if (currentBranch != null) {
                    branchComboBox.setSelectedItem(currentBranch);
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                // 如果获取失败，提供当前分支作为默认选项
                String currentBranch = getCurrentBranch();
                if (currentBranch != null) {
                    branchComboBox.addItem(currentBranch);
                }
            }
        });
    }
    
    private String getCurrentBranch() {
        try {
            GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.REV_PARSE);
            handler.addParameters("--abbrev-ref", "HEAD");
            
            GitCommandResult result = git.runCommand(handler);
            if (result.success() && !result.getOutput().isEmpty()) {
                return result.getOutput().get(0).trim();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "main"; // 默认分支名
    }
    
    private List<String> getRemoteBranches(String remote) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, new File(repository.getRoot().getPath()), GitCommand.BRANCH);
        handler.addParameters("-r");
        
        GitCommandResult result = git.runCommand(handler);
        if (!result.success()) {
            throw new Exception("Failed to get remote branches: " + String.join("\n", result.getErrorOutput()));
        }
        
        List<String> branches = new ArrayList<>();
        String remotePrefix = remote + "/";
        
        for (String line : result.getOutput()) {
            String branch = line.trim();
            if (branch.startsWith(remotePrefix)) {
                // 移除远程前缀，只保留分支名
                String branchName = branch.substring(remotePrefix.length());
                if (!branchName.contains("HEAD")) { // 跳过 HEAD 引用
                    branches.add(branchName);
                }
            }
        }
        
        return branches;
    }
    
    @Override
    protected ValidationInfo doValidate() {
        if (remoteComboBox.getSelectedItem() == null || 
            ((String) remoteComboBox.getSelectedItem()).trim().isEmpty()) {
            return new ValidationInfo("Please select a remote repository", remoteComboBox);
        }
        
        if (branchComboBox.getSelectedItem() == null || 
            ((String) branchComboBox.getSelectedItem()).trim().isEmpty()) {
            return new ValidationInfo("Please select or enter a target branch", branchComboBox);
        }
        
        return null;
    }
    
    public String getSelectedRemote() {
        Object selected = remoteComboBox.getSelectedItem();
        return selected != null ? selected.toString().trim() : "origin";
    }
    
    public String getSelectedBranch() {
        Object selected = branchComboBox.getSelectedItem();
        return selected != null ? selected.toString().trim() : "main";
    }
}