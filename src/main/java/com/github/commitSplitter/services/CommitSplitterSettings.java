package com.github.commitSplitter.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
    name = "com.github.commitSplitter.services.CommitSplitterSettings",
    storages = @Storage("CommitSplitterSettings.xml")
)
public class CommitSplitterSettings implements PersistentStateComponent<CommitSplitterSettings> {
    
    public List<UserInfo> users = new ArrayList<>();
    public SplitStrategy defaultStrategy = SplitStrategy.AUTO;
    public boolean showPreview = true;
    public boolean preserveOriginalCommit = false;
    
    public static CommitSplitterSettings getInstance() {
        return ApplicationManager.getApplication().getService(CommitSplitterSettings.class);
    }
    
    @Nullable
    @Override
    public CommitSplitterSettings getState() {
        return this;
    }
    
    @Override
    public void loadState(@NotNull CommitSplitterSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
    
    public static class UserInfo {
        public String username = "";
        public String email = "";
        // 密码不直接保存，通过PasswordSafe API管理
        private transient String password = "";
        
        public UserInfo() {}
        
        public UserInfo(String username, String email) {
            this.username = username;
            this.email = email;
        }
        
        public UserInfo(String username, String email, String password) {
            this.username = username;
            this.email = email;
            setPassword(password);
        }
        
        public String getPassword() {
            if (password == null || password.isEmpty()) {
                // 从安全存储中获取密码
                password = getPasswordFromSecureStorage();
            }
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
            // 保存到安全存储
            savePasswordToSecureStorage(password);
        }
        
        private String getPasswordFromSecureStorage() {
            try {
                com.intellij.credentialStore.CredentialAttributes credentialAttributes = 
                    new com.intellij.credentialStore.CredentialAttributes(
                        "CommitSplitter.password." + username,
                        username,
                        this.getClass(),
                        false
                    );
                
                com.intellij.credentialStore.Credentials credentials = 
                    com.intellij.ide.passwordSafe.PasswordSafe.getInstance().get(credentialAttributes);
                
                return credentials != null ? credentials.getPasswordAsString() : "";
            } catch (Exception e) {
                return "";
            }
        }
        
        private void savePasswordToSecureStorage(String password) {
            try {
                com.intellij.credentialStore.CredentialAttributes credentialAttributes = 
                    new com.intellij.credentialStore.CredentialAttributes(
                        "CommitSplitter.password." + username,
                        username,
                        this.getClass(),
                        false
                    );
                
                com.intellij.credentialStore.Credentials credentials = 
                    new com.intellij.credentialStore.Credentials(username, password);
                
                com.intellij.ide.passwordSafe.PasswordSafe.getInstance().set(credentialAttributes, credentials);
            } catch (Exception e) {
                // 如果保存失败，至少保留在内存中
            }
        }
        
        @Override
        public String toString() {
            return username + " <" + email + ">";
        }
    }
    
    public enum SplitStrategy {
        AUTO("Auto (Smart Selection)"),
        FILES("By Files"),
        HUNKS("By Code Hunks"),
        LINES("By Code Lines");

        private final String displayName;
        
        SplitStrategy(String displayName) {
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
}