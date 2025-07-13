package net.runelite.client.plugins.microbot.flippingCopilot;

import com.flippingcopilot.controller.FlippingCopilotAutomator;
import com.flippingcopilot.model.Suggestion;
import com.flippingcopilot.model.SuggestionManager;
import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

public class FlippingCopilotScript extends Script {

    public static double version = 1.0;

    enum State {
        IDLE,
        HANDLING_SUGGESTION
    }

    private State state = State.IDLE;
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
                state = State.IDLE; // Reset state when script is stopped
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

        // If the suggestion is invalid or a "wait" command, reset to IDLE
        // and clear the last suggestion so we can act on the next valid one.
        if (currentSuggestion == null || "wait".equals(currentSuggestion.getType())) {
            if (state == State.HANDLING_SUGGESTION) {
                state = State.IDLE;
                lastSuggestion = null;
            }
            return;
        }

        // If we are idle and the current suggestion is different from the last one we handled,
        // then we can proceed to handle this new suggestion.
        if (state == State.IDLE && !currentSuggestion.equals(lastSuggestion)) {
            lastSuggestion = currentSuggestion; // Remember the suggestion we are about to handle.
            state = State.HANDLING_SUGGESTION;
        }

        // If we are in the handling state, execute the action.
        if (state == State.HANDLING_SUGGESTION) {
            System.out.println("Handling suggestion: " + lastSuggestion.getType());
            switch (lastSuggestion.getType()) {
                case "buy":
                    automator.buy(lastSuggestion.getName(), lastSuggestion.getQuantity(), lastSuggestion.getPrice());
                    break;
                case "sell":
                    automator.sell(lastSuggestion.getName(), lastSuggestion.getQuantity(), lastSuggestion.getPrice());
                    break;
                case "abort":
                    automator.abort(lastSuggestion.getName(), true); // true to collect to bank
                    break;
            }
            // After handling, we don't reset to IDLE immediately.
            // We stay in HANDLING_SUGGESTION state until the suggestion is cleared (becomes null or "wait").
            // This prevents the script from re-executing the same suggestion.
        }
    }
}
