package net.runelite.client.plugins.microbot.flippingCopilot;

import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import com.flippingcopilot.controller.*;
import com.flippingcopilot.model.*;
import com.flippingcopilot.ui.MainPanel;
import com.flippingcopilot.ui.StatsPanelV2;
import com.google.gson.Gson;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "Flipping Copilot",
        description = "Your AI assistant for trading",
        tags = {"flipping", "microbot", "copilot"}
)
@Slf4j
public class FlippingCopilotPlugin extends Plugin {
    @Inject
    private FlippingCopilotConfig config;
    @Inject
    private ClientToolbar clientToolbar;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private FlippingCopilotOverlay overlay;
    @Inject
    private FlippingCopilotAutomator automator;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private ScheduledExecutorService executorService;
    @Inject
    private Gson gson;
    @Inject
    private GrandExchange grandExchange;
    @Inject
    private GrandExchangeCollectHandler grandExchangeCollectHandler;
    @Inject
    private GrandExchangeOfferEventHandler offerEventHandler;
    @Inject
    private ApiRequestHandler apiRequestHandler;
    @Inject
    private AccountStatusManager accountStatusManager;
    @Inject
    private SuggestionController suggestionController;
    @Inject
    private SuggestionManager suggestionManager;
    @Inject
    private WebHookController webHookController;
    @Inject
    private KeybindHandler keybindHandler;
    @Inject
    private CopilotLoginController copilotLoginController;
    @Inject
    private LoginResponseManager loginResponseManager;
    @Inject
    private HighlightController highlightController;
    @Inject
    private GameUiChangesHandler gameUiChangesHandler;
    @Inject
    private OsrsLoginManager osrsLoginManager;
    @Inject
    private FlipManager flipManager;
    @Inject
    private SessionManager sessionManager;
    @Inject
    private GrandExchangeUncollectedManager grandExchangeUncollectedManager;
    @Inject
    private TransactionManger transactionManger;
    @Inject
    private OfferManager offerManager;
    @Inject
    private TooltipController tooltipController;
    @Inject
    private MenuHandler menuHandler;

    private FlippingCopilotScript script;
    private MainPanel mainPanel;
    private StatsPanelV2 statsPanel;
    private NavigationButton navButton;
    private FileWriter debugWriter;

    @Provides
    FlippingCopilotConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FlippingCopilotConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        Rs2AntibanSettings.naturalMouse = true;
        try {
            debugWriter = new FileWriter("debug.txt", true);
            debugWriter.write("FlippingCopilotPlugin: startUp() called\n");
        } catch (IOException e) {
            log.error("Failed to open debug.txt", e);
        }

        Persistance.setUp(gson);

        mainPanel = injector.getInstance(MainPanel.class);
        mainPanel.setDebugWriter(debugWriter);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/net/runelite/client/plugins/microbot/flippingCopilot/icon-small.png");
        navButton = NavigationButton.builder()
                .tooltip("Flipping Copilot")
                .icon(icon)
                .priority(3)
                .panel(mainPanel)
                .build();
        clientToolbar.addNavigation(navButton);

        copilotLoginController.setLoginPanel(mainPanel.loginPanel);
        copilotLoginController.setMainPanel(mainPanel);
        suggestionController.setCopilotPanel(mainPanel.copilotPanel);
        suggestionController.setMainPanel(mainPanel);
        suggestionController.setLoginPanel(mainPanel.loginPanel);
        suggestionController.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
        grandExchangeCollectHandler.setSuggestionPanel(mainPanel.copilotPanel.suggestionPanel);
        mainPanel.copilotPanel.controlPanel.plugin = this;
        statsPanel = mainPanel.copilotPanel.statsPanel;

        mainPanel.refresh();
        if(loginResponseManager.isLoggedIn()) {
            flipManager.loadFlipsAsync();
        }
        if(osrsLoginManager.getInvalidStateDisplayMessage() == null) {
            flipManager.setIntervalDisplayName(osrsLoginManager.getPlayerDisplayName());
            flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
        }
        executorService.scheduleAtFixedRate(() -> {
            clientThread.invoke(() -> {
                boolean loginValid = osrsLoginManager.isValidLoginState();
                if (loginValid) {
                    AccountStatus accStatus = accountStatusManager.getAccountStatus();
                    boolean isFlipping = accStatus != null && accStatus.currentlyFlipping();
                    long cashStack = accStatus == null ? 0 : accStatus.currentCashStack();
                    if(sessionManager.updateSessionStats(isFlipping, cashStack)) {
                        mainPanel.copilotPanel.statsPanel.refresh(false, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
                    }
                }
            });
        }, 2000, 1000, TimeUnit.MILLISECONDS);


        if (overlayManager != null) {
            overlayManager.add(overlay);
        }

        script = new FlippingCopilotScript(automator, suggestionManager, accountStatusManager, mainPanel.copilotPanel.suggestionPanel);
    }

    public FlippingCopilotScript getScript() {
        return script;
    }

    @Override
    protected void shutDown() throws Exception {
        Rs2AntibanSettings.naturalMouse = false;
        try {
            debugWriter.write("FlippingCopilotPlugin: shutDown() called\n");
            debugWriter.close();
        } catch (IOException e) {
            log.error("Failed to close debug.txt", e);
        }

        offerManager.saveAll();
        highlightController.removeAll();
        clientToolbar.removeNavigation(navButton);
        if(loginResponseManager.isLoggedIn()) {
            String displayName = osrsLoginManager.getLastDisplayName();
            webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, displayName), sessionManager.getCachedSessionData(), displayName, false);
        }
        keybindHandler.unregister();
        overlayManager.remove(overlay);
        script.shutdown();
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        offerEventHandler.onGrandExchangeOfferChanged(event);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INV && grandExchange.isOpen()) {
            suggestionManager.setSuggestionNeeded(true);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        suggestionController.onGameTick();
        offerEventHandler.onGameTick();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        int slot = grandExchange.getOpenSlot();
        grandExchangeCollectHandler.handleCollect(event, slot);
        gameUiChangesHandler.handleMenuOptionClicked(event);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        tooltipController.tooltip(e);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        menuHandler.injectCopilotPriceGraphMenuEntry(event);
        menuHandler.injectConfirmMenuEntry(event);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        gameUiChangesHandler.onWidgetLoaded(event);
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event) {
        gameUiChangesHandler.onWidgetClosed(event);
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        gameUiChangesHandler.onVarbitChanged(event);
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged event) {
        gameUiChangesHandler.onVarClientStrChanged(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
                sessionManager.reset();
                suggestionManager.reset();
                osrsLoginManager.reset();
                accountStatusManager.reset();
                grandExchangeUncollectedManager.reset();
                statsPanel.refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
                mainPanel.refresh();
                break;
            case LOGGING_IN:
            case HOPPING:
            case CONNECTION_LOST:
                osrsLoginManager.setLastLoginTick(client.getTickCount());
                break;
            case LOGGED_IN:
                clientThread.invokeLater(() -> {
                    if (client.getGameState() != GameState.LOGGED_IN) {
                        return true;
                    }
                    final String name = osrsLoginManager.getPlayerDisplayName();
                    if(name == null) {
                        return false;
                    }
                    statsPanel.resetIntervalDropdownToSession();
                    flipManager.setIntervalDisplayName(name);
                    flipManager.setIntervalStartTime(sessionManager.getCachedSessionData().startTime);
                    statsPanel.refresh(true, loginResponseManager.isLoggedIn()  && osrsLoginManager.isValidLoginState());
                    mainPanel.refresh();
                    if(loginResponseManager.isLoggedIn()) {
                        transactionManger.scheduleSyncIn(0, name);
                    }
                    return true;
                });
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        gameUiChangesHandler.onVarClientIntChanged(event);
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown clientShutdownEvent) {
        log.debug("client shutdown event received");
        offerManager.saveAll();
        if(loginResponseManager.isLoggedIn()) {
            String displayName = osrsLoginManager.getLastDisplayName();
            webHookController.sendMessage(flipManager.calculateStats(sessionManager.getCachedSessionData().startTime, displayName), sessionManager.getCachedSessionData(), displayName, false);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("flippingcopilot")) {
            log.debug("copilot config changed event received");
            if (event.getKey().equals("profitAmountColor") || event.getKey().equals("lossAmountColor")) {
                mainPanel.copilotPanel.statsPanel.refresh(true, loginResponseManager.isLoggedIn() && osrsLoginManager.isValidLoginState());
            }
            if (event.getKey().equals("suggestionHighlights")) {
                clientThread.invokeLater(() -> highlightController.redraw());
            }
        }
    }
}