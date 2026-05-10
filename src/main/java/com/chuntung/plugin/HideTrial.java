package com.chuntung.plugin;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxFrameButton;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

public class HideTrial implements ProjectActivity {
    private static final Logger LOG = Logger.getInstance(HideTrial.class);

    @Override
    public @Nullable Object execute(@NonNull Project project, @NonNull Continuation<? super Unit> continuation) {
        LOG.warn("HideTrial plugin running");
        AnAction trialAction = ActionManager.getInstance().getAction("TrialStateWidget");
        if (trialAction != null) {
            LOG.warn("trialAction is not null");
            ActionManager.getInstance().unregisterAction("TrialStateWidget");
        }

        // 隐藏RustOver的状态栏中的"Trial"图标
        Thread t = new Thread(() -> {
            try {
                // 等待状态栏中的组件加载完毕
                TimeUnit.SECONDS.sleep(5);

                SwingUtilities.invokeLater(() -> {
                    // 获取当前项目 (Project) 的状态栏
                    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
                    if (statusBar != null) {
                        JComponent component = statusBar.getComponent();
                        if (component != null) {
                            Container contentPane = component.getRootPane().getContentPane();
                            for (Component contentPaneComponent : contentPane.getComponents()) {
                                // 找到状态栏
                                if (contentPaneComponent.getClass().getName().equals("com.intellij.openapi.wm.impl.status.IdeStatusBarImpl")) {
                                    IdeStatusBarImpl ideStatusBar = (IdeStatusBarImpl) contentPaneComponent;
                                    try {
                                        // 状态栏被分成3个部分：leftPanel、centralPanel、rightPanel
                                        // 我们要隐藏的图标位于rightPanel中
                                        Field rightPanelField = ReflectionUtil.findField(IdeStatusBarImpl.class, JPanel.class, "rightPanel");
                                        rightPanelField.setAccessible(true);
                                        JPanel rightPanel = (JPanel) rightPanelField.get(ideStatusBar);

                                        for (Component componentInRightPanel : rightPanel.getComponents()) {
                                            if (componentInRightPanel instanceof JPanel) {
                                                for (Component innerComponent : ((JPanel) componentInRightPanel).getComponents()) {
                                                    if (innerComponent.getClass().getName().contains("com.intellij.ide.ui.LicenseRelatedStatusBarWidget$createLabel$label")) {
                                                        LOG.info("找到图标了，开始隐藏它");
                                                        innerComponent.setVisible(false);
                                                    }
                                                }
                                            }
                                        }

                                    } catch (Exception e) {
                                        LOG.warn("Failed to get rightPanel field", e);
                                    }
                                }
                            }
                        }


                    } else {
                        LOG.warn("Failed to get statusBar");
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();

        hideTitleButtons(project, continuation);
        return null;
    }

    // 在niri上隐藏标题栏的最小化/最大化/关闭按钮
    public void hideTitleButtons(@NonNull Project project, @NonNull Continuation<? super Unit> continuation){
        LOG.warn("enter hideTitleButtons method");
        if ("niri".equals(System.getenv("XDG_CURRENT_DESKTOP"))){
            LOG.warn("XDG_CURRENT_DESKTOP is niri");
            IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(project);
            if (ideFrame != null){
                JComponent component = ideFrame.getComponent();
                LOG.warn("componentName:" + component.getName() + ",class: " + component.getClass());
                JRootPane rootPane = component.getRootPane();
                findAndHideComponent(
                        rootPane,
                        "com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxFrameButton",
                        (linuxFrameButton -> {
                            LOG.warn("Hide LinuxFrameButton: " + linuxFrameButton.getName());
                            linuxFrameButton.setVisible(false);
                            return null;
                        })
                );
            }
        }
    }

    private static void findAndHideComponent(Container parent, String className, Function<LinuxFrameButton, Void> handler) {
        if (parent == null) return;

        // 检查当前组件
        if (parent.getClass().getName().equals(className)) {
            handler.apply((LinuxFrameButton) parent);
            return;
        }

        // 递归检查子组件
        for (Component child : parent.getComponents()) {
            findAndHideComponent((Container) child, className, handler);
        }
    }
}
