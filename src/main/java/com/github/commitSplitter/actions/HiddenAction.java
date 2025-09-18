package com.github.commitSplitter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 用于隐藏不需要的actions的空实现
 */
public class HiddenAction extends AnAction {
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 什么都不做，用来覆盖原有的action
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 隐藏这个action
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
    }
}