package com.github.commitSplitter.ui;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.intellij.openapi.application.ApplicationManager;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RemoteConfigDialog extends DialogWrapper {
    private JComboBox<String> remoteComboBox;
    private JComboBox<String> branchComboBox;
    private final List<CommitSplitterSettings.UserInfo> users;
    private final List<UserEntry> userEntries = new ArrayList<>();
    private final boolean requireRemoteSelection;
    private final Project project;
    private final GitRepository repository;
    private final git4idea.commands.Git git;
    private final String commitMessage;

    public RemoteConfigDialog(Project project,
                              GitRepository repository,
                              List<CommitSplitterSettings.UserInfo> users,
                              boolean requireRemoteSelection,
                              String commitMessage) {
        super(project);
        this.project = project;
        this.repository = repository;
        this.git = git4idea.commands.Git.getInstance();
        this.users = users;
        this.requireRemoteSelection = requireRemoteSelection;
        this.commitMessage = commitMessage != null ? commitMessage : "";

        setTitle("Remote Repository Configuration");
        setOKButtonText("Continue Split");
        setCancelButtonText("Cancel");

        init();
        if (requireRemoteSelection) {
            loadRemoteData();
        }
    }

    @Override
    public @NotNull Dimension getPreferredSize() {
        return new Dimension(700, super.getPreferredSize().height);
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

            row++;
            gbc.gridx = 0; gbc.gridy = row;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Target Branch:"), gbc);

            branchComboBox = new JComboBox<>();
            branchComboBox.setPreferredSize(new Dimension(200, branchComboBox.getPreferredSize().height));
            branchComboBox.setEditable(true);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(branchComboBox, gbc);

            row++;
            gbc.gridx = 0; gbc.gridy = row;
            gbc.gridwidth = 2;
            JLabel helpLabel = new JLabel("<html><i>Select the remote repository and target branch for pushing split commits.<br/>" +
                    "You can type a new branch name if it doesn't exist yet.</i></html>");
            helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
            panel.add(helpLabel, gbc);

            row++;
        } else {
            remoteComboBox = new JComboBox<>();
            branchComboBox = new JComboBox<>();
        }

        gbc.gridx = 0; gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel selectHeader = new JLabel("Select Users");
        selectHeader.setFont(selectHeader.getFont().deriveFont(Font.BOLD));
        panel.add(selectHeader, gbc);

        row++;

        if (users == null || users.isEmpty()) {
            gbc.gridx = 0; gbc.gridy = row;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            panel.add(new JLabel("No users configured. Configure users in Settings → Tools → Commit Splitter."), gbc);
            row++;
        } else {
            for (CommitSplitterSettings.UserInfo user : users) {
                String displayName = user.username;
                if (user.email != null && !user.email.isEmpty()) {
                    displayName += " <" + user.email + ">";
                }

                JCheckBox checkBox = new JCheckBox();
                checkBox.setSelected(true);

                JLabel userLabel = new JLabel(displayName);

                String defaultMessage = buildDefaultMessage(user, commitMessage);
                JTextField messageField = new JTextField(defaultMessage, 60);
                messageField.setToolTipText("Edit the full commit message for this user");

                userEntries.add(new UserEntry(user, checkBox, messageField));

                gbc.gridx = 0; gbc.gridy = row;
                gbc.gridwidth = 1;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                gbc.insets = JBUI.insets(2, 15, 2, 5);
                panel.add(checkBox, gbc);

                gbc.gridx = 1; gbc.gridy = row;
                gbc.weightx = 0.0;
                gbc.fill = GridBagConstraints.NONE;
                gbc.insets = JBUI.insets(2, 5, 2, 5);
                panel.add(userLabel, gbc);

                row++;

                gbc.gridx = 0; gbc.gridy = row;
                gbc.gridwidth = 2;
                gbc.weightx = 1.0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                gbc.insets = JBUI.insets(2, 40, 2, 0);
                panel.add(messageField, gbc);

                row++;
            }
        }

        return panel;
    }

    private String buildDefaultMessage(CommitSplitterSettings.UserInfo user, String original) {
        if (original == null || original.isEmpty()) {
            return "";
        }
        String cleaned = original.trim();
        String username = user.username != null ? user.username.trim() : "";
        if (!username.isEmpty()) {
            cleaned = cleaned.replaceFirst("^\\s*@" + Pattern.quote(username) + "\\s+", "");
        }
        return cleaned;
    }

    private void loadRemoteData() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<String> remotes = getAvailableRemotes();
                String currentBranch = getCurrentBranch();
                List<String> remoteBranches = remotes.isEmpty() ? null : getRemoteBranches(remotes.get(remotes.contains("origin") ? remotes.indexOf("origin") : 0));

                SwingUtilities.invokeLater(() -> {
                    remoteComboBox.removeAllItems();
                    for (String remote : remotes) {
                        remoteComboBox.addItem(remote);
                    }

                    if (remotes.contains("origin")) {
                        remoteComboBox.setSelectedItem("origin");
                    } else if (!remotes.isEmpty()) {
                        remoteComboBox.setSelectedIndex(0);
                    }

                    branchComboBox.removeAllItems();
                    if (currentBranch != null && !currentBranch.trim().isEmpty()) {
                        branchComboBox.addItem(currentBranch);
                    }
                    if (remoteBranches != null) {
                        for (String branch : remoteBranches) {
                            if (!branch.equals(currentBranch)) {
                                branchComboBox.addItem(branch);
                            }
                        }
                    }
                    if (currentBranch != null) {
                        branchComboBox.setSelectedItem(currentBranch);
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    remoteComboBox.removeAllItems();
                    remoteComboBox.addItem("origin");
                    branchComboBox.removeAllItems();
                    branchComboBox.addItem("main");
                });
            }
        });
    }

    private List<String> getAvailableRemotes() throws Exception {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.REMOTE);

        GitCommandResult result;
        try {
            result = git.runCommand(handler);
        } catch (Throwable t) {
            throw new Exception("Failed to get remotes: " + t.getMessage(), t);
        }
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

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String currentBranch = getCurrentBranch();
                List<String> remoteBranches = getRemoteBranches(selectedRemote);

                SwingUtilities.invokeLater(() -> {
                    branchComboBox.removeAllItems();
                    if (currentBranch != null && !currentBranch.trim().isEmpty()) {
                        branchComboBox.addItem(currentBranch);
                    }
                    for (String branch : remoteBranches) {
                        if (!branch.equals(currentBranch)) {
                            branchComboBox.addItem(branch);
                        }
                    }
                    if (currentBranch != null) {
                        branchComboBox.setSelectedItem(currentBranch);
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    branchComboBox.removeAllItems();
                    branchComboBox.addItem("main");
                });
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
        return "main";
    }

    private List<String> getRemoteBranches(String remote) throws Exception {
        GitLineHandler handler = new GitLineHandler(project, repository.getRoot(), GitCommand.BRANCH);
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
                String branchName = branch.substring(remotePrefix.length());
                if (!branchName.contains("HEAD")) {
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

        List<UserEntry> selected = getSelectedEntries();
        if (selected.isEmpty()) {
            return new ValidationInfo("Please select at least one user", userEntries.isEmpty() ? null : userEntries.get(0).checkBox);
        }

        for (UserEntry entry : selected) {
            String text = entry.messageField.getText();
            if (text == null || text.trim().isEmpty()) {
                return new ValidationInfo("Please provide a commit message for " + entry.user.username, entry.messageField);
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

    public List<CommitSplitterSettings.UserInfo> getSelectedUsers() {
        return getSelectedEntries().stream()
                .map(e -> e.user)
                .collect(Collectors.toList());
    }

    public Map<String, String> getUserMessages() {
        Map<String, String> result = new LinkedHashMap<>();
        for (UserEntry entry : getSelectedEntries()) {
            String text = entry.messageField.getText();
            if (text != null) {
                result.put(entry.user.username, text.trim());
            }
        }
        return result;
    }

    private List<UserEntry> getSelectedEntries() {
        List<UserEntry> selected = new ArrayList<>();
        for (UserEntry entry : userEntries) {
            if (entry.checkBox.isSelected()) {
                selected.add(entry);
            }
        }
        return selected;
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

    private static class UserEntry {
        final CommitSplitterSettings.UserInfo user;
        final JCheckBox checkBox;
        final JTextField messageField;

        UserEntry(CommitSplitterSettings.UserInfo user, JCheckBox checkBox, JTextField messageField) {
            this.user = user;
            this.checkBox = checkBox;
            this.messageField = messageField;
        }
    }
}
