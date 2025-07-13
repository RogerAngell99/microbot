package com.flippingcopilot.controller;

import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FlippingCopilotAutomator {

    private final OfferHandler offerHandler;

    public void buy(String itemName, int quantity, int price) {
        Rs2GrandExchange.buyItem(itemName, price, quantity);
    }

    public void sell(String itemName, int quantity, int price) {
        Rs2GrandExchange.sellItem(itemName, quantity, price);
    }

    public void abort(String itemName, boolean collectToBank) {
        Rs2GrandExchange.abortOffer(itemName, collectToBank);
    }

    public void setQuantity(int quantity) {
        if (offerHandler.isSettingQuantity()) {
            Rs2GrandExchange.setChatboxValue(quantity);
            Rs2Keyboard.enter();
        }
    }

    public void setValue(int price) {
        if (offerHandler.isSettingPrice()) {
            Rs2GrandExchange.setChatboxValue(price);
            Rs2Keyboard.enter();
        }
    }
}