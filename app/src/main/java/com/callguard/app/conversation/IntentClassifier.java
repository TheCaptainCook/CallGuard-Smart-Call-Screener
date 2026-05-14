package com.callguard.app.conversation;

import android.util.Log;

import java.util.Locale;

/**
 * IntentClassifier — Phase 4 NLP module to categorize caller intent.
 * 
 * Analyzes the STT transcript to determine the caller's purpose and
 * suggests dynamic responses.
 */
public class IntentClassifier {

    private static final String TAG = "CG_IntentClassifier";

    public enum IntentType {
        DELIVERY,
        APPOINTMENT,
        SPAM,
        URGENT,
        UNKNOWN
    }

    /**
     * Categorizes the intent from the transcript.
     */
    public static IntentType classifyIntent(String transcript) {
        if (transcript == null || transcript.isEmpty()) {
            return IntentType.UNKNOWN;
        }

        String lowerText = transcript.toLowerCase(Locale.ROOT);

        if (lowerText.matches(".*\\b(delivery|package|amazon|fedex|ups|courier|drop off)\\b.*")) {
            return IntentType.DELIVERY;
        }
        if (lowerText.matches(".*\\b(appointment|schedule|doctor|dentist|meeting|booking|reservation)\\b.*")) {
            return IntentType.APPOINTMENT;
        }
        if (lowerText.matches(".*\\b(offer|insurance|loan|credit card|warranty|selected|won|prize)\\b.*")) {
            return IntentType.SPAM;
        }
        if (lowerText.matches(".*\\b(urgent|emergency|hospital|police|accident|help|quick)\\b.*")) {
            return IntentType.URGENT;
        }

        return IntentType.UNKNOWN;
    }

    /**
     * Generates a dynamic response template based on the detected intent.
     */
    public static String generateResponse(IntentType intent) {
        switch (intent) {
            case DELIVERY:
                return "If this is a delivery, please leave the package at the front door. Thank you.";
            case APPOINTMENT:
                return "If this is about an appointment, please leave the details and I will call back to confirm.";
            case URGENT:
                return "I understand this is urgent. I will notify the person immediately. Please leave a detailed message.";
            case SPAM:
                return "We are not interested. Please remove this number from your list.";
            case UNKNOWN:
            default:
                return GreetingEngine.DEFAULT_FAREWELL;
        }
    }
}
