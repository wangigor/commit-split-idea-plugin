package com.github.commitSplitter.ui;

import com.github.commitSplitter.services.CommitSplitterSettings;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RemoteConfigDialog extends DialogWrapper {
    private JComboBox<String> remoteComboBox;
    private JComboBox<String> branchComboBox;
    private final List<CommitSplitterSettings.UserInfo> users;
    private final List<UserPrefixEntry> userPrefixEntries = new ArrayList<>();
    private final boolean requireRemoteSelection;
    private final Project project;
    private final GitRepository repository;
    private final git4idea.commands.Git git;

    public RemoteConfigDialog(Project project,
                              GitRepository repository,
                              List<CommitSplitterSettings.UserInfo> users,
                              boolean requireRemoteSelection) {
        super(project);
        this.project = project;
        this.repository = repository;
        this.git = git4idea.commands.Git.getInstance();
        this.users = users;
        this.requireRemoteSelection = requireRemoteSelection;

        setTitle("Remote Repository Configuration");
        setOKButtonText("Continue Split");
        setCancelButtonText("Cancel");

        init();
        if (requireRemoteSelection) {
            loadRemoteData();
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        if (requireRemoteSelection) {
            // Remote repository selection
            gbc.gridx = 0; gbc.gridy = row;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Remote Repository:"), gbc);

            remoteComboBox = new JComboBox<>();
            remoteComboBox.setPreferredSize(new Dimension(200, remoteComboBox.getPreferredSize().height));
            remoteComboBox.addActionListener(e -> loadBranchesForSelectedRemote());

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(remoteComboBox, gbc);

            // Branch selection
            row++;
            gbc.gridx = 0; gbc.gridy = row;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Target Branch:"), gbc);

            branchComboBox = new JComboBox<>();
            branchComboBox.setPreferredSize(new Dimension(200, branchComboBox.getPreferredSize().height));
            branchComboBox.setEditable(true); // allow new branch input

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(branchComboBox, gbc);

            // Help text
            row++;
            gbc.gridx = 0; gbc.gridy = row;
            gbc.gridwidth = 2;
            JLabel helpLabel = new JLabel("<html><i>Select the remote repository and target branch for pushing split commits.<br/>" +
                    "You can type a new branch name if it doesn't exist yet.</i></html>");
            helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
            panel.add(helpLabel, gbc);

            row++;
        } else {
            // When no remote selection is required, initialise combos to avoid NPE in getters
            remoteComboBox = new JComboBox<>();
            branchComboBox = new JComboBox<>();
        }

        // Prefix section header
        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel prefixHeader = new JLabel("Commit Message Prefixes");
        prefixHeader.setFont(prefixHeader.getFont().deriveFont(Font.BOLD));
        panel.add(prefixHeader, gbc);

        row++;

        if (users != null && !users.isEmpty()) {
            for (CommitSplitterSettings.UserInfo user : users) {
                String displayName = user.username;
                if (user.email != null && !user.email.isEmpty()) {
                    displayName += " <" + user.email + ">";
                }

                gbc.gridx = 0; gbc.gridy = row;
                gbc.gridwidth = 1;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                panel.add(new JLabel(displayName + ":"), gbc);

                JTextField prefixField = new JTextField(defaultPrefixFor(user), 25);
                prefixField.setToolTipText("Prefix that will replace the beginning of the commit message for this user");

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                panel.add(prefixField, gbc);

                userPrefixEntries.add(new UserPrefixEntry(user, prefixField));
                row++;
            }
        } else {
            gbc.gridx = 0; gbc.gridy = row;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(new JLabel("No users configured. Configure users in Settings → Tools → Commit Splitter."), gbc);
        }

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
        if (requireRemoteSelection) {
            if (remoteComboBox.getSelectedItem() == null ||
                ((String) remoteComboBox.getSelectedItem()).trim().isEmpty()) {
                return new ValidationInfo("Please select a remote repository", remoteComboBox);
            }

            if (branchComboBox.getSelectedItem() == null ||
                ((String) branchComboBox.getSelectedItem()).trim().isEmpty()) {
                return new ValidationInfo("Please select or enter a target branch", branchComboBox);
            }
        }

        for (UserPrefixEntry entry : userPrefixEntries) {
            String value = entry.prefixField.getText();
            if (value == null || value.trim().isEmpty()) {
                return new ValidationInfo("Please provide a prefix for " + entry.user.username, entry.prefixField);
            }
        }

        return null;
    }

    public String getSelectedRemote() {
        Object selected = remoteComboBox.getSelectedItem();
        return selected != null ? selected.toString().trim() : "origin";
    }

    public String getSelectedBranch() {
        Object selected = branchComboBox.getSelectedItem();
        return normalizeBranch(selected != null ? selected.toString() : "main");
    }

    public Map<String, String> getUserPrefixes() {
        Map<String, String> result = new LinkedHashMap<>();
        for (UserPrefixEntry entry : userPrefixEntries) {
            String prefix = entry.prefixField.getText();
            if (prefix != null) {
                result.put(entry.user.username, prefix.trim());
            }
        }
        return result;
    }

    private String defaultPrefixFor(CommitSplitterSettings.UserInfo user) {
        String username = user.username != null ? user.username : "";
        if (username.isEmpty()) {
            return "";
        }
        return "@" + username;
    }

    private String normalizeBranch(String rawBranch) {
        if (rawBranch == null) {
            return "main";
        }

        String trimmed = rawBranch.trim();
        if (trimmed.isEmpty()) {
            return "main";
        }

        if (trimmed.startsWith("remotes/")) {
            trimmed = trimmed.substring("remotes/".length());
        }

        if (requireRemoteSelection) {
            Object remoteItem = remoteComboBox.getSelectedItem();
            if (remoteItem != null) {
                String remoteName = remoteItem.toString();
                String remotePrefix = remoteName + "/";
                if (trimmed.startsWith(remotePrefix)) {
                    trimmed = trimmed.substring(remotePrefix.length());
                }
            }
        }

        if (trimmed.startsWith("refs/heads/")) {
            trimmed = trimmed.substring("refs/heads/".length());
        } else if (trimmed.startsWith("heads/")) {
            trimmed = trimmed.substring("heads/".length());
        }

        return trimmed;
    }

    private static class UserPrefixEntry {
        private final CommitSplitterSettings.UserInfo user;
        private final JTextField prefixField;

        private UserPrefixEntry(CommitSplitterSettings.UserInfo user, JTextField prefixField) {
            this.user = user;
            this.prefixField = prefixField;
        }
    }
}
