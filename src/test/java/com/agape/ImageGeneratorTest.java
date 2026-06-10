package com.agape;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Regression tests for design-code decoding (reads assets/design_codes.json,
 * which is part of the repository — no test data is written).
 */
public class ImageGeneratorTest {

    @Test
    public void nullCodeFallsBackToDefaults() {
        String[] paths = ImageGenerator.decodeDesignCode(null);
        assertEquals("assets/backgrounds/default.png", paths[0]);
        assertEquals("assets/frames/default.png", paths[1]);
    }

    @Test
    public void codeWithoutDashFallsBackToDefaults() {
        String[] paths = ImageGenerator.decodeDesignCode("HEGSPK");
        assertEquals("assets/backgrounds/default.png", paths[0]);
        assertEquals("assets/frames/default.png", paths[1]);
    }

    @Test
    public void defaultCodeResolvesToDefaultFiles() {
        String[] paths = ImageGenerator.decodeDesignCode("HEG-SPK");
        assertEquals("assets/backgrounds/default.png", paths[0]);
        assertEquals("assets/frames/default.png", paths[1]);
    }

    @Test
    public void knownCodeResolvesToConfiguredFiles() {
        String[] paths = ImageGenerator.decodeDesignCode("BTW-PST");
        assertEquals("assets/backgrounds/Breath of the Wild.png", paths[0]);
        assertEquals("assets/frames/Painted Strokes.png", paths[1]);
    }

    @Test
    public void codesAreCaseInsensitive() {
        String[] paths = ImageGenerator.decodeDesignCode("btw-pst");
        assertEquals("assets/backgrounds/Breath of the Wild.png", paths[0]);
        assertEquals("assets/frames/Painted Strokes.png", paths[1]);
    }

    @Test
    public void unknownCodesFallBackToDefaults() {
        String[] paths = ImageGenerator.decodeDesignCode("ZZZ-QQQ");
        assertEquals("assets/backgrounds/default.png", paths[0]);
        assertEquals("assets/frames/default.png", paths[1]);
    }
}
