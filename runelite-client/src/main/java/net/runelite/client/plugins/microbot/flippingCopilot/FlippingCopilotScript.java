package net.runelite.client.plugins.microbot.flippingCopilot;

import com.flippingcopilot.controller.FlippingCopilotAutomator;
import com.flippingcopilot.model.AccountStatusManager;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import com.flippingcopilot.ui.SuggestionPanel;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class FlippingCopilotScript extends Script {

    public static double version = 1.0;

    private Suggestion lastSuggestion = null;

    private final FlippingCopilotAutomator automator;
    private final SuggestionManager suggestionManager;
    private final AccountStatusManager accountStatusManager;
    private final SuggestionPanel suggestionPanel;

    @Inject
    public FlippingCopilotScript(FlippingCopilotAutomator automator, SuggestionManager suggestionManager, AccountStatusManager accountStatusManager, SuggestionPanel suggestionPanel) {
        this.automator = automator;
        this.suggestionManager = suggestionManager;
        this.accountStatusManager = accountStatusManager;
        this.suggestionPanel = suggestionPanel;
    }

    public boolean run(FlippingCopilotConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) {
                return;
            }
            Rs2Antiban.setActivity(Activity.GENERAL_CRAFTING);
            try {
                loop();
            } catch (Exception e) {
                System.out.println("FlippingCopilotScript Error: " + e.toString());
                e.printStackTrace();
                super.shutdown();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop() {
        String suggestedActionType = suggestionPanel.getSuggestedActionType();
        String suggestedItemName = suggestionPanel.getSuggestedItemName();
        int suggestedQuantity = suggestionPanel.getSuggestedQuantity();
        int suggestedPrice = suggestionPanel.getSuggestedPrice();

        Suggestion currentSuggestion = suggestionManager.getSuggestion();

        // If there is no suggestion or it's a "wait" command, reset our memory of the last suggestion.
        // This makes the script ready for a completely new suggestion.
        if (suggestedActionType.equals("wait")) {
            lastSuggestion = null;
            return;
        }

        // If the current suggestion is the same one we just handled, do nothing.
        // This prevents the script from spamming the same action.
        // Note: This comparison is simplified as SuggestionPanel doesn't provide a full Suggestion object.
        // A more robust comparison would involve all relevant fields.
        if (lastSuggestion != null &&
            suggestedActionType.equals(lastSuggestion.getType()) &&
            (suggestedItemName == null || suggestedItemName.equals(lastSuggestion.getName()))) {
            return;
        }

        System.out.println("Handling new suggestion from panel: " + suggestedActionType);

        if (suggestedActionType.equals("unknown")) {
            System.out.println("Unknown suggestion type from panel.");
            return;
        }

        switch (suggestedActionType) {
            case "buy":
                // Use SuggestionManager for slot information
                int buySlot = currentSuggestion != null ? currentSuggestion.getBoxId() : -1; // Assuming boxId is the slot
                automator.buy(suggestedItemName, suggestedQuantity, suggestedPrice); // Need to pass slot to automator.buy
                break;
            case "sell":
                automator.sell(suggestedItemName, suggestedQuantity, suggestedPrice);
                break;
            case "abort":
                // Use SuggestionManager for slot information
                int abortSlot = currentSuggestion != null ? currentSuggestion.getBoxId() : -1; // Assuming boxId is the slot
                automator.abort(suggestedItemName, true); // Need to pass slot to automator.abort
                break;
            case "collect":
                automator.collect();
                break;
        }

        // Remember the suggestion we just acted on so we don't act on it again in the next loop.
        // Create a dummy Suggestion object for lastSuggestion based on panel data
        lastSuggestion = new Suggestion(suggestedActionType, -1, -1, suggestedPrice, suggestedQuantity, suggestedItemName, -1, null, null);
    }
}
