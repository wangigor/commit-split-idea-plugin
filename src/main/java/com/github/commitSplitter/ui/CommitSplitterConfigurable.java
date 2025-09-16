package com.github.commitSplitter.ui;

import com.github.commitSplitter.services.CommitSplitterSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommitSplitterConfigurable implements Configurable {
    
    private JPanel mainPanel;
    private UserTableModel tableModel;
    private JBTable userTable;
    private JComboBox<CommitSplitterSettings.SplitStrategy> strategyComboBox;
    private JCheckBox showPreviewCheckBox;
    private JCheckBox preserveOriginalCheckBox;
    
    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Commit Splitter";
    }
    
    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel(new BorderLayout());
        
        // 创建设置面板
        JPanel settingsPanel = createSettingsPanel();
        mainPanel.add(settingsPanel, BorderLayout.NORTH);
        
        // 创建用户表格
        JPanel tablePanel = createUserTablePanel();
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        
        return mainPanel;
    }
    
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // 拆分策略
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(new JLabel("Default Split Strategy:"), gbc);
        
        gbc.gridx = 1;
        strategyComboBox = new JComboBox<>(CommitSplitterSettings.SplitStrategy.values());
        panel.add(strategyComboBox, gbc);
        
        // 显示预览
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        showPreviewCheckBox = new JCheckBox("Show preview before splitting");
        panel.add(showPreviewCheckBox, gbc);
        
        // 保留原始提交
        gbc.gridy = 2;
        preserveOriginalCheckBox = new JCheckBox("Preserve original commit");
        panel.add(preserveOriginalCheckBox, gbc);
        
        return panel;
    }
    
    private JPanel createUserTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 表格标题
        JLabel titleLabel = new JLabel("Users Configuration:");
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 创建表格
        tableModel = new UserTableModel();
        userTable = new JBTable(tableModel);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // 添加工具栏
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(userTable)
                .setAddAction(e -> addUser())
                .setRemoveAction(e -> removeUser())
                .setEditAction(e -> editUser());
        
        panel.add(decorator.createPanel(), BorderLayout.CENTER);
        
        return panel;
    }
    
    private void addUser() {
        UserEditDialog dialog = new UserEditDialog(mainPanel, "Add User", "", "", "");
        if (dialog.showAndGet()) {
            tableModel.addUser(new CommitSplitterSettings.UserInfo(
                    dialog.getUsername(), dialog.getEmail(), dialog.getPassword()));
        }
    }
    
    private void removeUser() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            tableModel.removeUser(selectedRow);
        }
    }
    
    private void editUser() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow >= 0) {
            CommitSplitterSettings.UserInfo user = tableModel.getUser(selectedRow);
            UserEditDialog dialog = new UserEditDialog(mainPanel, "Edit User", 
                    user.username, user.email, user.getPassword());
            if (dialog.showAndGet()) {
                user.username = dialog.getUsername();
                user.email = dialog.getEmail();
                user.setPassword(dialog.getPassword());
                tableModel.fireTableRowsUpdated(selectedRow, selectedRow);
            }
        }
    }
    
    @Override
    public boolean isModified() {
        CommitSplitterSettings settings = CommitSplitterSettings.getInstance();
        return !settings.users.equals(tableModel.users) ||
               settings.defaultStrategy != strategyComboBox.getSelectedItem() ||
               settings.showPreview != showPreviewCheckBox.isSelected() ||
               settings.preserveOriginalCommit != preserveOriginalCheckBox.isSelected();
    }
    
    @Override
    public void apply() throws ConfigurationException {
        // 验证用户输入
        if (tableModel.users.isEmpty()) {
            throw new ConfigurationException("At least one user must be configured.");
        }
        
        for (CommitSplitterSettings.UserInfo user : tableModel.users) {
            if (user.username.trim().isEmpty() || user.email.trim().isEmpty()) {
                throw new ConfigurationException("All users must have both username and email.");
            }
        }
        
        CommitSplitterSettings settings = CommitSplitterSettings.getInstance();
        settings.users.clear();
        settings.users.addAll(tableModel.users);
        settings.defaultStrategy = (CommitSplitterSettings.SplitStrategy) strategyComboBox.getSelectedItem();
        settings.showPreview = showPreviewCheckBox.isSelected();
        settings.preserveOriginalCommit = preserveOriginalCheckBox.isSelected();
    }
    
    @Override
    public void reset() {
        CommitSplitterSettings settings = CommitSplitterSettings.getInstance();
        tableModel.setUsers(new ArrayList<>(settings.users));
        strategyComboBox.setSelectedItem(settings.defaultStrategy);
        showPreviewCheckBox.setSelected(settings.showPreview);
        preserveOriginalCheckBox.setSelected(settings.preserveOriginalCommit);
    }
    
    private static class UserTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Username", "Email", "Password"};
        private List<CommitSplitterSettings.UserInfo> users = new ArrayList<>();
        
        public void setUsers(List<CommitSplitterSettings.UserInfo> users) {
            this.users = users;
            fireTableDataChanged();
        }
        
        public void addUser(CommitSplitterSettings.UserInfo user) {
            users.add(user);
            fireTableRowsInserted(users.size() - 1, users.size() - 1);
        }
        
        public void removeUser(int index) {
            users.remove(index);
            fireTableRowsDeleted(index, index);
        }
        
        public CommitSplitterSettings.UserInfo getUser(int index) {
            return users.get(index);
        }
        
        @Override
        public int getRowCount() {
            return users.size();
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
            CommitSplitterSettings.UserInfo user = users.get(rowIndex);
            switch (columnIndex) {
                case 0: return user.username;
                case 1: return user.email;
                case 2: {
                    String password = user.getPassword();
                    return password != null && !password.isEmpty() ? "●●●●●●●●" : "";
                }
                default: return "";
            }
        }
    }
}