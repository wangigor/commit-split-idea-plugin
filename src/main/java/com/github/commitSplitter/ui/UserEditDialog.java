package com.github.commitSplitter.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class UserEditDialog extends DialogWrapper {
    private JTextField usernameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    
    public UserEditDialog(Component parent, String title, String username, String email, String password) {
        super(parent, true);
        setTitle(title);
        usernameField = new JTextField(username, 20);
        emailField = new JTextField(email, 20);
        passwordField = new JPasswordField(password, 20);
        init();
    }
    
    // 向后兼容的构造函数
    public UserEditDialog(Component parent, String title, String username, String email) {
        this(parent, title, username, email, "");
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(usernameField, gbc);
        
        // Email
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Email:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(emailField, gbc);
        
        // Password
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(passwordField, gbc);
        
        // Add help text for password
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel helpLabel = new JLabel("<html><i>Password/Token is optional. Used for pushing commits to remote repository.<br/>" +
                "For GitHub: Use Personal Access Token instead of password.<br/>" +
                "For GitLab: Use Personal Access Token or Deploy Token.<br/>" +
                "Leave empty to skip pushing.</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(helpLabel, gbc);
        
        return panel;
    }
    
    @Override
    protected void doOKAction() {
        if (StringUtil.isEmptyOrSpaces(usernameField.getText())) {
            JOptionPane.showMessageDialog(getContentPane(), 
                    "Username cannot be empty", "Validation Error", 
                    JOptionPane.ERROR_MESSAGE);
            usernameField.requestFocus();
            return;
        }
        
        if (StringUtil.isEmptyOrSpaces(emailField.getText()) || 
            !emailField.getText().contains("@")) {
            JOptionPane.showMessageDialog(getContentPane(), 
                    "Please enter a valid email address", "Validation Error", 
                    JOptionPane.ERROR_MESSAGE);
            emailField.requestFocus();
            return;
        }
        
        super.doOKAction();
    }
    
    public String getUsername() {
        return usernameField.getText().trim();
    }
    
    public String getEmail() {
        return emailField.getText().trim();
    }
    
    public String getPassword() {
        return new String(passwordField.getPassword());
    }
}