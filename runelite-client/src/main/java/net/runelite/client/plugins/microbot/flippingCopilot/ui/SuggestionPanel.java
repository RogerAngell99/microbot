package com.flippingcopilot.ui;

import net.runelite.client.plugins.microbot.flippingCopilot.FlippingCopilotConfig;
import com.flippingcopilot.controller.GrandExchange;
import com.flippingcopilot.controller.HighlightController;
import com.flippingcopilot.controller.PremiumInstanceController;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.graph.PriceGraphController;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;

import static com.flippingcopilot.ui.UIUtilities.*;
import static com.flippingcopilot.util.Constants.MIN_GP_NEEDED_TO_FLIP;


@Singleton
@Slf4j
public class SuggestionPanel extends JPanel {

    // dependencies
    private final FlippingCopilotConfig config;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    public final PauseButton pauseButton;
    private final BlockButton blockButton;
    private final OsrsLoginManager osrsLoginManager;
    private final Client client;
    private final PausedManager pausedManager;
    private final GrandExchangeUncollectedManager uncollectedManager;
    private final ClientThread clientThread;
    private final HighlightController highlightController;
    private final ItemManager itemManager;
    private final GrandExchange grandExchange;
    private final PriceGraphController priceGraphController;
    private final PremiumInstanceController premiumInstanceController;
	private final FlipManager flipManager;

    private final JLabel suggestionText = new JLabel();
    private final JLabel suggestionIcon = new JLabel(new ImageIcon(ImageUtil.loadImageResource(getClass(),"/net/runelite/client/plugins/microbot/flippingCopilot/small_open_arrow.png")));
    private final JPanel suggestionTextContainer = new JPanel();
    public final Spinner spinner = new Spinner();
    private JLabel skipButton;
    private final JPanel buttonContainer = new JPanel();
    private JLabel graphButton;
    private final JPanel suggestedActionPanel;
    private final PreferencesPanel preferencesPanel;
    private final JLayeredPane layeredPane = new JLayeredPane();
    private boolean isPreferencesPanelVisible = false;
    private final JLabel gearButton;
    private String innerSuggestionMessage;
    private String highlightedColor = "yellow";
    private FileWriter debugWriter;

    @Setter
    private String serverMessage = "";


    @Inject
    public SuggestionPanel(FlippingCopilotConfig config,
                           SuggestionManager suggestionManager,
                           AccountStatusManager accountStatusManager,
                           PauseButton pauseButton,
                           BlockButton blockButton,
                           PreferencesPanel preferencesPanel,
                           OsrsLoginManager osrsLoginManager,
                           Client client, PausedManager pausedManager,
                           GrandExchangeUncollectedManager uncollectedManager,
                           ClientThread clientThread,
                           HighlightController highlightController,
                           ItemManager itemManager,
                           GrandExchange grandExchange, PriceGraphController priceGraphController, PremiumInstanceController premiumInstanceController,
                           FlipManager flipManager) {
        this.preferencesPanel = preferencesPanel;
        this.config = config;
        this.suggestionManager = suggestionManager;
        this.accountStatusManager = accountStatusManager;
        this.pauseButton = pauseButton;
        this.blockButton = blockButton;
        this.osrsLoginManager = osrsLoginManager;
        this.client = client;
        this.pausedManager = pausedManager;
        this.uncollectedManager = uncollectedManager;
        this.clientThread = clientThread;
        this.highlightController = highlightController;
        this.itemManager = itemManager;
        this.grandExchange = grandExchange;
        this.priceGraphController = priceGraphController;
        this.premiumInstanceController = premiumInstanceController;
        this.flipManager = flipManager;

        // Create the layered pane first
        layeredPane.setLayout(null);  // LayeredPane needs null layout

        // Create a main panel that will hold all the regular components
        suggestedActionPanel = new JPanel(new BorderLayout());
        suggestedActionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestedActionPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));
        suggestedActionPanel.setBounds(0, 0, 300, 150);  // Set appropriate size
        JLabel title = new JLabel("<html><center> <FONT COLOR=white><b>Suggested Action:" +
                "</b></FONT></center></html>");
        title.setHorizontalAlignment(SwingConstants.CENTER);
        suggestedActionPanel.add(title, BorderLayout.NORTH);

        JPanel suggestionContainer = new JPanel();
        suggestionContainer.setLayout(new CardLayout());
        suggestionContainer.setOpaque(true);
        suggestionContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionContainer.setPreferredSize(new Dimension(0, 85));
        suggestedActionPanel.add(suggestionContainer, BorderLayout.CENTER);

        suggestionTextContainer.setLayout(new BoxLayout(suggestionTextContainer, BoxLayout.X_AXIS));
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.add(suggestionIcon);
        suggestionTextContainer.add(suggestionText);
        suggestionTextContainer.add(Box.createHorizontalGlue());
        suggestionTextContainer.setOpaque(true);
        suggestionTextContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setVisible(false);
        suggestionIcon.setOpaque(true);
        suggestionIcon.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        suggestionIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        suggestionText.setHorizontalAlignment(SwingConstants.CENTER);
        suggestionText.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        suggestionContainer.add(suggestionTextContainer);

        suggestionContainer.add(spinner);
        setupButtonContainer();
        suggestedActionPanel.add(buttonContainer, BorderLayout.SOUTH);


        layeredPane.add(suggestedActionPanel, JLayeredPane.DEFAULT_LAYER);

        // Build the suggestion preferences panel:
        this.preferencesPanel.setVisible(false);
        layeredPane.add(this.preferencesPanel, JLayeredPane.DEFAULT_LAYER);

        // Create and add the gear button
        BufferedImage gearIcon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/microbot/flippingCopilot/preferences-icon.png");
        gearIcon = ImageUtil.resizeImage(gearIcon, 20, 20);
        BufferedImage recoloredIcon = ImageUtil.recolorImage(gearIcon, ColorScheme.LIGHT_GRAY_COLOR);
        gearButton = buildButton(recoloredIcon, "Settings", () -> {});
        gearButton.setEnabled(true);
        gearButton.setFocusable(true);
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setOpaque(true);
        ImageIcon iconOff = new ImageIcon(recoloredIcon);
        ImageIcon iconOn = new ImageIcon(ImageUtil.luminanceScale(recoloredIcon, BUTTON_HOVER_LUMINANCE));
        // Replace the existing gear button MouseAdapter with this implementation
        gearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isEventDispatchThread()) {
                    SwingUtilities.invokeLater(() -> handleGearClick());
                    return;
                }
                handleGearClick();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                gearButton.setIcon(iconOn);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                gearButton.setIcon(iconOff);
            }
        });
        gearButton.setOpaque(true);
        gearButton.setBounds(5, 5, 20, 20);
        layeredPane.add(gearButton, JLayeredPane.PALETTE_LAYER);

        // Set up the main panel
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPreferredSize(new Dimension(0, 150));

        add(layeredPane);

        // Add a component listener to handle resizing
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                preferencesPanel.setBounds(0, 0, getWidth(), getHeight());
                suggestedActionPanel.setBounds(0, 0, getWidth(), getHeight());
                layeredPane.setPreferredSize(new Dimension(getWidth(), getHeight()));
            }
        });
    }

    public void setDebugWriter(FileWriter debugWriter) {
        this.debugWriter = debugWriter;
    }

    // Add this as a private method in the class
    private void handleGearClick() {
//        Data data = getPriceData();
//
//        Manager.showPriceGraph(graphButton, data);


        isPreferencesPanelVisible = !isPreferencesPanelVisible;
        preferencesPanel.setVisible(isPreferencesPanelVisible);
        suggestedActionPanel.setVisible(!isPreferencesPanelVisible);
        refresh();
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    private void setupButtonContainer() {
        buttonContainer.setLayout(new BorderLayout());
        buttonContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
        JPanel centerPanel = new JPanel(new GridLayout(1, 5, 15, 0));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
    
        BufferedImage graphIcon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/microbot/flippingCopilot/graph.png");
        graphButton = buildButton(graphIcon, "Price graph", () -> {
            if(config.priceGraphWebsite().equals(FlippingCopilotConfig.PriceGraphWebsite.FLIPPING_COPILOT)) {
                Suggestion suggestion = suggestionManager.getSuggestion();
                priceGraphController.showPriceGraph( suggestion.getName(),true);
            } else {
                Suggestion suggestion = suggestionManager.getSuggestion();
                String url = config.priceGraphWebsite().getUrl(suggestion.getName(), suggestion.getItemId());
                LinkBrowser.browse(url);
            }
        });
        centerPanel.add(graphButton);
    
        JPanel emptyPanel = new JPanel();
        emptyPanel.setOpaque(false);
        centerPanel.add(emptyPanel);
        centerPanel.add(pauseButton);
        centerPanel.add(blockButton);
    
        BufferedImage skipIcon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/microbot/flippingCopilot/skip.png");
        skipButton = buildButton(skipIcon, "Skip suggestion", () -> {
            showLoading();
            Suggestion s = suggestionManager.getSuggestion();
            accountStatusManager.setSkipSuggestion(s != null ? s.getId() : -1);
            suggestionManager.setSuggestionNeeded(true);
        });
        centerPanel.add(skipButton);
        
        buttonContainer.add(centerPanel, BorderLayout.CENTER);
    }


    private void setItemIcon(int itemId) {
        AsyncBufferedImage image = itemManager.getImage(itemId);
        if (image != null) {
            image.addTo(suggestionIcon);
            suggestionIcon.setVisible(true);
        }
    }


    public void updateSuggestion(Suggestion suggestion) {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        String suggestionString = "<html><center>";
        suggestionTextContainer.setVisible(false);
        String innerMessage = "";

        switch (suggestion.getType()) {
            case "wait":
                innerMessage = "Wait <br>";
                break;
            case "abort":
                innerMessage = "Abort offer for<br><FONT COLOR=white>" + suggestion.getName() + "<br></FONT>";
                setItemIcon(suggestion.getItemId());
                break;
            case "buy":
            case "sell":
                String capitalisedAction = suggestion.getType().equals("buy") ? "Buy" : "Sell";
                innerMessage = capitalisedAction +
                        " <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getQuantity()) + "</FONT><br>" +
                        "<FONT COLOR=white>" + suggestion.getName() + "</FONT><br>" +
                        "for <FONT COLOR=" + highlightedColor + ">" + formatter.format(suggestion.getPrice()) + "</FONT> gp<br>";
                if (suggestion.getType().equals("sell")) {
                    long profit = 0;
                    FlipV2 lastFlip = flipManager.getLastFlipByItemId(osrsLoginManager.getPlayerDisplayName(), suggestion.getItemId());
                    if (lastFlip != null) {
                        profit = lastFlip.calculateProfit(suggestion.getItemId(), suggestion.getQuantity(), suggestion.getPrice());
                    }
                    if (profit > 0) {
                        innerMessage += "<FONT COLOR=green>(+" + formatter.format(profit) + " gp)</FONT>";
                    } else if (profit < 0) {
                        innerMessage += "<FONT COLOR=red>(" + formatter.format(profit) + " gp)</FONT>";
                    }
                }
                setItemIcon(suggestion.getItemId());
                break;
            default:
                innerMessage = "Error processing suggestion<br>";
        }
        innerMessage += suggestion.getMessage();
        suggestionString += innerMessage;
        suggestionString += "</center><html>";
        innerSuggestionMessage = innerMessage;
        if(!suggestion.getType().equals("wait")) {
            setButtonsVisible(true);
        }
        suggestionText.setText(suggestionString);
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        suggestionTextContainer.setVisible(true);
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    public void suggestCollect() {
        setMessage("Collect items");
        setButtonsVisible(false);
    }

    public void suggestAddGp() {
        NumberFormat formatter = NumberFormat.getNumberInstance();
        setMessage("Add " +
                "at least <FONT COLOR=" + highlightedColor + ">" + formatter.format(MIN_GP_NEEDED_TO_FLIP)
                               + "</FONT> gp<br>to your inventory<br>" +
                               "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void suggestOpenGe() {
        setMessage("Open the Grand Exchange<br>" +
                "to get a flip suggestion");
        setButtonsVisible(false);
    }

    public void setIsPausedMessage() {
        setMessage("Suggestions are paused");
        setButtonsVisible(false);
    }

    public void setMessage(String message) {
        innerSuggestionMessage = message;
        setButtonsVisible(false);

        // Check if message contains "<manage>"
        String displayMessage = message;
        if (message != null && message.contains("<manage>")) {
            // Replace <manage> with a styled link
            displayMessage = message.replace("<manage>",
                    "<a href='#' style='text-decoration:underline'>manage</a>");

            // Add mouse listener if not already present
            boolean hasListener = false;
            for (MouseListener listener : suggestionText.getMouseListeners()) {
                if (listener instanceof ManageClickListener) {
                    hasListener = true;
                    break;
                }
            }

            if (!hasListener) {
                suggestionText.addMouseListener(new ManageClickListener());
                // Make the label show a hand cursor when hovering over it
                suggestionText.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        } else {
            suggestionText.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
        suggestionText.setText("<html><center>" + displayMessage + "<br>" + serverMessage + "</center></html>");
        suggestionText.setMaximumSize(new Dimension(suggestionText.getPreferredSize().width, Integer.MAX_VALUE));
        suggestionTextContainer.revalidate();
        suggestionTextContainer.repaint();
    }

    private class ManageClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            String text = suggestionText.getText();
            if (text.contains("manage")) {
                premiumInstanceController.loadAndOpenPremiumInstanceDialog();
            }
        }
    }

    public boolean isCollectItemsSuggested() {
        return suggestionText.isVisible() && "Collect items".equals(innerSuggestionMessage);
    }

    public String getSuggestedActionType() {
        if (isCollectItemsSuggested()) {
            return "collect";
        } else if (innerSuggestionMessage.contains("Buy")) {
            return "buy";
        } else if (innerSuggestionMessage.contains("Sell")) {
            return "sell";
        } else if (innerSuggestionMessage.contains("Abort")) {
            return "abort";
        } else if (innerSuggestionMessage.contains("Wait")) {
            return "wait";
        }
        return "unknown";
    }

    public String getSuggestedItemName() {
        if (innerSuggestionMessage == null) {
            return null;
        }

        // For buy/sell
        String startTag = "<FONT COLOR=white>";
        String endTag = "</FONT><br>for";
        int startIndex = innerSuggestionMessage.indexOf(startTag);
        int endIndex = innerSuggestionMessage.indexOf(endTag);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return innerSuggestionMessage.substring(startIndex + startTag.length(), endIndex).trim();
        }

        // For abort
        startTag = "for<br><FONT COLOR=white>";
        endTag = "<br></FONT>";
        startIndex = innerSuggestionMessage.indexOf(startTag);
        endIndex = innerSuggestionMessage.indexOf(endTag);

        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return innerSuggestionMessage.substring(startIndex + startTag.length(), endIndex).trim();
        }

        return null;
    }

    public int getSuggestedQuantity() {
        // This is a simplified extraction. A more robust solution might parse the HTML or store these values directly.
        if (innerSuggestionMessage.contains("FONT COLOR=") && innerSuggestionMessage.contains("</FONT><br><FONT COLOR=white>")) {
            int startIndex = innerSuggestionMessage.indexOf("FONT COLOR=") + "FONT COLOR=".length() + 7; // +7 for color name and ">
            int endIndex = innerSuggestionMessage.indexOf("</FONT><br><FONT COLOR=white>");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                try {
                    return Integer.parseInt(innerSuggestionMessage.substring(startIndex, endIndex).replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse quantity from: " + innerSuggestionMessage, e);
                }
            }
        }
        return -1;
    }

    public int getSuggestedPrice() {
        // This is a simplified extraction. A more robust solution might parse the HTML or store these values directly.
        if (innerSuggestionMessage.contains("for <FONT COLOR=") && innerSuggestionMessage.contains("</FONT> gp<br>")) {
            int startIndex = innerSuggestionMessage.indexOf("for <FONT COLOR=") + "for <FONT COLOR=".length() + 7; // +7 for color name and ">
            int endIndex = innerSuggestionMessage.indexOf("</FONT> gp<br>");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                try {
                    return Integer.parseInt(innerSuggestionMessage.substring(startIndex, endIndex).replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price from: " + innerSuggestionMessage, e);
                }
            }
        }
        return -1;
    }

    public void showLoading() {
        suggestionTextContainer.setVisible(false);
        setServerMessage("");
        spinner.show();
        setButtonsVisible(false);
        suggestionIcon.setVisible(false);
        suggestionText.setText("");
    }

    public void hideLoading() {
        spinner.hide();
        suggestionTextContainer.setVisible(true);
    }

    private void setButtonsVisible(boolean visible) {
        skipButton.setVisible(visible);
        blockButton.setVisible(visible);
        graphButton.setVisible(visible);
        suggestionIcon.setVisible(visible);
    }

    public void displaySuggestion() {
        Suggestion suggestion = suggestionManager.getSuggestion();
        if (suggestion == null) {
            return;
        }
        AccountStatus accountStatus = accountStatusManager.getAccountStatus();
        if(accountStatus == null) {
            return;
        }
        setServerMessage(suggestion.getMessage());
        boolean collectNeeded = accountStatus.isCollectNeeded(suggestion);
        if(collectNeeded && !uncollectedManager.HasUncollected(osrsLoginManager.getAccountHash())) {
            log.warn("tick {} collect is suggested but there is nothing to collect! suggestion: {} {} {}", client.getTickCount(), suggestion.getType(), suggestion.getQuantity(), suggestion.getItemId());
        }
        if (collectNeeded) {
            suggestCollect();
        } else if(suggestion.getType().equals("wait") && !grandExchange.isOpen() && accountStatus.emptySlotExists()) {
            suggestOpenGe();
        }else if (suggestion.getType().equals("wait") && accountStatus.moreGpNeeded()) {
            suggestAddGp();
        }  else {
            updateSuggestion(suggestion);
        }
        highlightController.redraw();
    }

    public void refresh() {
        try {
            if (debugWriter != null) {
                debugWriter.write("SuggestionPanel: refresh() called\n");
            }
        } catch (IOException e) {
            // ignore
        }
        log.debug("refreshing suggestion panel {}", client.getGameState());
        if(!SwingUtilities.isEventDispatchThread()) {
            // we always execute this in the Swing EDT thread
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if(isPreferencesPanelVisible) {
            preferencesPanel.refresh();
        }
        if (pausedManager.isPaused()) {
            setIsPausedMessage();
            hideLoading();
            return;
        }

        String errorMessage = osrsLoginManager.getInvalidStateDisplayMessage();
        if (errorMessage != null) {
            setMessage(errorMessage);
            hideLoading();
        }

        if(suggestionManager.isSuggestionRequestInProgress()) {
            showLoading();
            return;
        }
        hideLoading();

        final HttpResponseException suggestionError = suggestionManager.getSuggestionError();
        if(suggestionError != null) {
            highlightController.redraw();
            setMessage("Error: " + suggestionError.getMessage());
            return;
        }

        if(!client.isClientThread()) {
            clientThread.invoke(this::displaySuggestion);
        } else {
            displaySuggestion();
        }
    }
}