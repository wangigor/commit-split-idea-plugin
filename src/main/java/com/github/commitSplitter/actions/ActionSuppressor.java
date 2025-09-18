package com.github.commitSplitter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 隐藏IDEA核心的Go to Parent/Child Commit actions
 */
public class ActionSuppressor implements StartupActivity {
    
    @Override
    public void runActivity(@NotNull Project project) {
        // 延迟执行，确保所有actions都已经加载
        ApplicationManager.getApplication().invokeLater(() -> {
            hideUnwantedActions();
        });
    }
    
    private void hideUnwantedActions() {
        ActionManager actionManager = ActionManager.getInstance();
        
        // 从构建日志中我们知道确切的action IDs
        String[] actionsToHide = {
            "Vcs.Log.GoToParent",  // "Go to Parent Commit"
            "Vcs.Log.GoToChild"   // "Go to Child Commit"
        };
        
        System.out.println("ActionSuppressor: Starting to hide unwanted actions...");
        
        for (String actionId : actionsToHide) {
            try {
                AnAction action = actionManager.getAction(actionId);
                if (action != null) {
                    System.out.println("Found action to hide: " + actionId);
                    
                    // 方法1：尝试从VCS Log上下文菜单中移除
                    DefaultActionGroup vcsLogMenu = (DefaultActionGroup) actionManager.getAction("Vcs.Log.ContextMenu");
                    if (vcsLogMenu != null) {
                        vcsLogMenu.remove(action);
                        System.out.println("Removed action from VCS Log menu: " + actionId);
                    }
                    
                    // 方法2：注销action
                    actionManager.unregisterAction(actionId);
                    System.out.println("Unregistered action: " + actionId);
                    
                } else {
                    System.out.println("Action not found: " + actionId);
                }
            } catch (Exception e) {
                System.err.println("Failed to hide action " + actionId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("ActionSuppressor: Finished processing actions");
    }
}