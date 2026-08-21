package com.carya.energynews.translation;

public record TranslationOutput(
        String translatedTitle,
        String translatedDescription,
        String translatedContent
) {

    public TranslationOutput(String translatedTitle, String translatedDescription) {
        this(translatedTitle, translatedDescription, null);
    }
}
