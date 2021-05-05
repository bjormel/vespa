// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.opennlp;

import com.yahoo.language.Language;
import com.yahoo.language.LinguisticsCase;
import com.yahoo.language.process.Normalizer;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.language.process.TokenType;
import com.yahoo.language.process.Tokenizer;
import com.yahoo.language.process.Transformer;
import com.yahoo.language.simple.SimpleNormalizer;
import com.yahoo.language.simple.SimpleToken;
import com.yahoo.language.simple.SimpleTokenType;
import com.yahoo.language.simple.SimpleTokenizer;
import com.yahoo.language.simple.SimpleTransformer;
import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tokenizer using OpenNlp
 *
 * @author matskin
 */
public class OpenNlpTokenizer implements Tokenizer {

    private final static int SPACE_CODE = 32;
    private final Normalizer normalizer;
    private final Transformer transformer;
    private final SimpleTokenizer simpleTokenizer;

    public OpenNlpTokenizer() {
        this(new SimpleNormalizer(), new SimpleTransformer());
    }

    public OpenNlpTokenizer(Normalizer normalizer, Transformer transformer) {
        this.normalizer = normalizer;
        this.transformer = transformer;
        simpleTokenizer = new SimpleTokenizer(normalizer, transformer);
    }

    @Override
    public Iterable<Token> tokenize(String input, Language language, StemMode stemMode, boolean removeAccents) {
        if (input.isEmpty()) return Collections.emptyList();
        Stemmer stemmer = stemmerFor(language, stemMode);
        if (stemmer == null) return simpleTokenizer.tokenize(input, language, stemMode, removeAccents);

        List<Token> tokens = new ArrayList<>();
        int nextCode = input.codePointAt(0);
        TokenType prevType = SimpleTokenType.valueOf(nextCode);
        for (int prev = 0, next = Character.charCount(nextCode); next <= input.length(); ) {
            nextCode = next < input.length() ? input.codePointAt(next) : SPACE_CODE;
            TokenType nextType = SimpleTokenType.valueOf(nextCode);
            if (!prevType.isIndexable() || !nextType.isIndexable()) {
                String original = input.substring(prev, next);
                String token = processToken(original, language, stemMode, removeAccents, stemmer);
                tokens.add(new SimpleToken(original).setOffset(prev).setType(prevType).setTokenString(token));
                prev = next;
                prevType = nextType;
            }
            next += Character.charCount(nextCode);
        }
        return tokens;
    }

    private String processToken(String token, Language language, StemMode stemMode, boolean removeAccents,
                                Stemmer stemmer) {
        token = normalizer.normalize(token);
        token = LinguisticsCase.toLowerCase(token);
        if (removeAccents)
            token = transformer.accentDrop(token, language);
        if (stemMode != StemMode.NONE)
            token = stemmer.stem(token).toString();
        return token;
    }

    private Stemmer stemmerFor(Language language, StemMode stemMode) {
        if (language == null || language == Language.ENGLISH || stemMode == StemMode.NONE) return null;
        SnowballStemmer.ALGORITHM algorithm = algorithmFor(language);
        if (algorithm == null) return null;
        return new SnowballStemmer(algorithm);
    }

    private SnowballStemmer.ALGORITHM algorithmFor(Language language) {
        switch (language) {
            case DANISH: return SnowballStemmer.ALGORITHM.DANISH;
            case DUTCH: return SnowballStemmer.ALGORITHM.DUTCH;
            case FINNISH: return SnowballStemmer.ALGORITHM.FINNISH;
            case FRENCH: return SnowballStemmer.ALGORITHM.FRENCH;
            case GERMAN: return SnowballStemmer.ALGORITHM.GERMAN;
            case HUNGARIAN: return SnowballStemmer.ALGORITHM.HUNGARIAN;
            case IRISH: return SnowballStemmer.ALGORITHM.IRISH;
            case ITALIAN: return SnowballStemmer.ALGORITHM.ITALIAN;
            case NORWEGIAN_BOKMAL: return SnowballStemmer.ALGORITHM.NORWEGIAN;
            case NORWEGIAN_NYNORSK: return SnowballStemmer.ALGORITHM.NORWEGIAN;
            case PORTUGUESE: return SnowballStemmer.ALGORITHM.PORTUGUESE;
            case ROMANIAN: return SnowballStemmer.ALGORITHM.ROMANIAN;
            case RUSSIAN: return SnowballStemmer.ALGORITHM.RUSSIAN;
            case SPANISH: return SnowballStemmer.ALGORITHM.SPANISH;
            case SWEDISH: return SnowballStemmer.ALGORITHM.SWEDISH;
            case TURKISH: return SnowballStemmer.ALGORITHM.TURKISH;
            case ENGLISH: return SnowballStemmer.ALGORITHM.ENGLISH;
            default: return null;
        }
    }

}
