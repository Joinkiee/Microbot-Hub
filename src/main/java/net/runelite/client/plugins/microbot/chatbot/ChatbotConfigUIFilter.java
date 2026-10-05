package net.runelite.client.plugins.microbot.chatbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

/**
 * Dynamically filters the 'Model' dropdown (aiModel) in RuneLite's Config Panel
 * so that it only displays AI models corresponding to the selected AI Provider (aiProvider).
 */
@Slf4j
public class ChatbotConfigUIFilter {

    private static final String CLIENT_PROP_FILTERED = "chatbot.model.filtered";
    private static final String CLIENT_PROP_POPUP_ATTACHED = "chatbot.model.popup_attached";

    private static AWTEventListener awtEventListener = null;

    /**
     * Installs the dynamic UI filter.
     */
    public static synchronized void install(ChatbotConfig config, ConfigManager configManager) {
        uninstall();

        awtEventListener = event -> {
            if (event instanceof HierarchyEvent || event instanceof ContainerEvent || event instanceof MouseEvent) {
                SwingUtilities.invokeLater(() -> findAndFilterModelBoxes(config, configManager));
            }
        };

        try {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                awtEventListener,
                AWTEvent.HIERARCHY_EVENT_MASK | AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
            );
        } catch (Exception e) {
            log.warn("[Chatbot] Failed to register AWTEventListener for config UI filter: {}", e.getMessage());
        }

        SwingUtilities.invokeLater(() -> findAndFilterModelBoxes(config, configManager));
    }

    /**
     * Uninstalls the dynamic UI filter.
     */
    public static synchronized void uninstall() {
        if (awtEventListener != null) {
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(awtEventListener);
            } catch (Exception ignored) {
            }
            awtEventListener = null;
        }
    }

    /**
     * Handles provider changes by updating the stored aiModel configuration
     * and refreshing any open dropdown in the UI.
     */
    public static void onProviderChanged(ChatbotConfig config, ConfigManager configManager) {
        if (config == null || configManager == null) {
            return;
        }

        ChatbotProvider newProvider = config.aiProvider();
        ChatbotModel currentModel = config.aiModel();
        ChatbotModel[] allowedModels = ChatbotModel.getModelsForProvider(newProvider);
        List<ChatbotModel> allowedList = Arrays.asList(allowedModels);

        ChatbotModel targetModel;
        if (currentModel != null && allowedList.contains(currentModel)) {
            targetModel = currentModel;
        } else {
            targetModel = ChatbotModel.getDefaultModelForProvider(newProvider);
            log.debug("[Chatbot] Active provider '{}' defaults to model '{}' in-memory", newProvider, targetModel.name());
        }

        final ChatbotModel selectedModel = targetModel;
        SwingUtilities.invokeLater(() -> {
            findAndFilterModelBoxes(config, configManager);
            for (Frame frame : Frame.getFrames()) {
                updateModelComboBoxSelection(frame, selectedModel);
            }
        });
    }

    /**
     * Recursively traverses all active frames to find and filter any ChatbotModel JComboBox.
     */
    public static void findAndFilterModelBoxes(ChatbotConfig config, ConfigManager configManager) {
        if (config == null) return;
        ChatbotProvider provider = config.aiProvider();
        for (Frame frame : Frame.getFrames()) {
            filterContainer(frame, provider, config, configManager);
        }
    }

    @SuppressWarnings("unchecked")
    private static void filterContainer(Container container, ChatbotProvider provider, ChatbotConfig config, ConfigManager configManager) {
        if (container == null) return;

        for (Component component : container.getComponents()) {
            if (component instanceof JComboBox) {
                JComboBox<?> box = (JComboBox<?>) component;
                if (isChatbotModelComboBox(box)) {
                    setupModelComboBox((JComboBox<ChatbotModel>) box, provider, config, configManager);
                }
            } else if (component instanceof Container) {
                filterContainer((Container) component, provider, config, configManager);
            }
        }
    }

    private static boolean isChatbotModelComboBox(JComboBox<?> box) {
        if (box == null) return false;
        if (Boolean.TRUE.equals(box.getClientProperty(CLIENT_PROP_FILTERED))) {
            return true;
        }
        ComboBoxModel<?> model = box.getModel();
        if (model != null && model.getSize() > 0) {
            Object first = model.getElementAt(0);
            if (first instanceof ChatbotModel) {
                box.putClientProperty(CLIENT_PROP_FILTERED, Boolean.TRUE);
                return true;
            }
        }
        Object selected = box.getSelectedItem();
        if (selected instanceof ChatbotModel) {
            box.putClientProperty(CLIENT_PROP_FILTERED, Boolean.TRUE);
            return true;
        }
        return false;
    }

    private static void setupModelComboBox(JComboBox<ChatbotModel> box, ChatbotProvider provider, ChatbotConfig config, ConfigManager configManager) {
        // Attach popup menu listener once to dynamically ensure models match whenever clicked
        if (!Boolean.TRUE.equals(box.getClientProperty(CLIENT_PROP_POPUP_ATTACHED))) {
            box.putClientProperty(CLIENT_PROP_POPUP_ATTACHED, Boolean.TRUE);
            box.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    ChatbotProvider currentProv = config.aiProvider();
                    applyModelFilter(box, currentProv, config);
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {
                }
            });
        }

        applyModelFilter(box, provider, config);
    }

    private static void applyModelFilter(JComboBox<ChatbotModel> box, ChatbotProvider provider, ChatbotConfig config) {
        ChatbotModel[] targetModels = ChatbotModel.getModelsForProvider(provider);
        ComboBoxModel<ChatbotModel> currentModel = box.getModel();

        // Check if the current items already match targetModels
        boolean matches = currentModel.getSize() == targetModels.length;
        if (matches) {
            for (int i = 0; i < targetModels.length; i++) {
                if (currentModel.getElementAt(i) != targetModels[i]) {
                    matches = false;
                    break;
                }
            }
        }

        if (!matches) {
            ChatbotModel currentSelection = (ChatbotModel) box.getSelectedItem();
            DefaultComboBoxModel<ChatbotModel> newModel = new DefaultComboBoxModel<>(targetModels);
            box.setModel(newModel);

            List<ChatbotModel> targetList = Arrays.asList(targetModels);
            if (currentSelection != null && targetList.contains(currentSelection)) {
                box.setSelectedItem(currentSelection);
            } else {
                ChatbotModel defaultModel = ChatbotModel.getDefaultModelForProvider(provider);
                box.setSelectedItem(defaultModel);
            }
            box.repaint();
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateModelComboBoxSelection(Container container, ChatbotModel targetModel) {
        if (container == null) return;
        for (Component c : container.getComponents()) {
            if (c instanceof JComboBox) {
                JComboBox<?> box = (JComboBox<?>) c;
                if (isChatbotModelComboBox(box)) {
                    JComboBox<ChatbotModel> modelBox = (JComboBox<ChatbotModel>) box;
                    if (modelBox.getSelectedItem() != targetModel) {
                        modelBox.setSelectedItem(targetModel);
                        modelBox.repaint();
                    }
                }
            } else if (c instanceof Container) {
                updateModelComboBoxSelection((Container) c, targetModel);
            }
        }
    }
}
