package com.github.commitSplitter.services;

public class RemoteConfig {
    private final String remoteName;
    private final String branchName;
    
    public RemoteConfig(String remoteName, String branchName) {
        this.remoteName = remoteName != null ? remoteName.trim() : "origin";
        this.branchName = branchName != null ? branchName.trim() : "main";
    }
    
    public String getRemoteName() {
        return remoteName;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    @Override
    public String toString() {
        return String.format("RemoteConfig{remote='%s', branch='%s'}", remoteName, branchName);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        RemoteConfig that = (RemoteConfig) obj;
        return remoteName.equals(that.remoteName) && branchName.equals(that.branchName);
    }
    
    @Override
    public int hashCode() {
        return remoteName.hashCode() * 31 + branchName.hashCode();
    }
}