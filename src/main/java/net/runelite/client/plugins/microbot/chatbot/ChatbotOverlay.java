package net.runelite.client.plugins.microbot.chatbot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class ChatbotOverlay extends OverlayPanel {

    private final ChatbotScript chatbotScript;
    private final ChatbotConfig config;

    @Inject
    ChatbotOverlay(ChatbotPlugin plugin, ChatbotScript chatbotScript, ChatbotConfig config) {
        super(plugin);
        this.chatbotScript = chatbotScript;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(230, 200));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("\uD83E\uDD16 Chatbot V" + ChatbotPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            String modelDisplay = chatbotScript.getActiveModelDisplay();
            if (modelDisplay == null || modelDisplay.isEmpty()) {
                modelDisplay = ChatbotModel.getModelName(config);
            }
            if (modelDisplay.length() > 22) {
                modelDisplay = modelDisplay.substring(0, 22);
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Model:")
                    .right(modelDisplay)
                    .rightColor(Color.WHITE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(chatbotScript.getDisplayStatus())
                    .rightColor(chatbotScript.getStatusColor())
                    .build());

            String currentQuery = chatbotScript.getCurrentQuery();
            if (currentQuery != null && !currentQuery.isEmpty()) {
                String q = currentQuery;
                if (q.length() > 20) {
                    q = q.substring(0, 20) + "...";
                }
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Heard:")
                        .right(q)
                        .rightColor(new Color(200, 200, 200))
                        .build());
            }

            String reasoning = chatbotScript.getLastReasoning();
            if (reasoning != null && !reasoning.isEmpty()) {
                String r = reasoning;
                if (r.length() > 20) {
                    r = r.substring(0, 20) + "...";
                }
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Reason:")
                        .right(r)
                        .rightColor(Color.CYAN)
                        .build());
            }

            String lastResp = chatbotScript.getLastResponse();
            if (lastResp != null && !lastResp.isEmpty()) {
                String r = lastResp;
                if (r.length() > 20) {
                    r = r.substring(0, 20) + "...";
                }
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Reply:")
                        .right(r)
                        .rightColor(new Color(120, 255, 120))
                        .build());
            }

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Activity:")
                    .right("Rx: " + chatbotScript.getMessagesReceived() + " | Tx: " + chatbotScript.getResponsesSent())
                    .rightColor(Color.WHITE)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Microbot:")
                    .right(Microbot.status)
                    .build());

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
