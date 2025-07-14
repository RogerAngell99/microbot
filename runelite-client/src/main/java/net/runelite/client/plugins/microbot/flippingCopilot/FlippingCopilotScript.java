package net.runelite.client.plugins.microbot.flippingCopilot;

import com.flippingcopilot.controller.FlippingCopilotAutomator;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

public class FlippingCopilotScript extends Script {

    public static double version = 1.0;

    private Suggestion lastSuggestion = null;

    private final FlippingCopilotAutomator automator;
    private final SuggestionManager suggestionManager;

    public FlippingCopilotScript(FlippingCopilotAutomator automator, SuggestionManager suggestionManager) {
        this.automator = automator;
        this.suggestionManager = suggestionManager;
    }

    public boolean run(FlippingCopilotConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) {
                return;
            }
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
        Suggestion currentSuggestion = suggestionManager.getSuggestion();

        // If there is no suggestion or it's a "wait" command, reset our memory of the last suggestion.
        // This makes the script ready for a completely new suggestion.
        if (currentSuggestion == null || "wait".equals(currentSuggestion.getType())) {
            lastSuggestion = null;
            return;
        }

        // If the current suggestion is the same one we just handled, do nothing.
        // This prevents the script from spamming the same action.
        if (currentSuggestion.equals(lastSuggestion)) {
            return;
        }

        // At this point, we have a new, valid suggestion. Let's handle it.
        System.out.println("Handling new suggestion: " + currentSuggestion.getType());
        switch (currentSuggestion.getType()) {
            case "buy":
                automator.buy(currentSuggestion.getName(), currentSuggestion.getQuantity(), currentSuggestion.getPrice());
                break;
            case "sell":
                automator.sell(currentSuggestion.getName(), currentSuggestion.getQuantity(), currentSuggestion.getPrice());
                break;
            case "abort":
                automator.abort(currentSuggestion.getName(), true); // true to collect to bank
                break;
        }

        // Remember the suggestion we just acted on so we don't act on it again in the next loop.
        lastSuggestion = currentSuggestion;
    }
}
