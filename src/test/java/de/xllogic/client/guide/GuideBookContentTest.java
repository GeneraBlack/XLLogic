package de.xllogic.client.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuideBookContentTest {
    @Test
    void guideBookDefinesMultiplePagesWithCodeExamples() {
        assertTrue(GuideBookContent.pages().size() >= 6, "expected the in-game guide to provide multiple pages");
        assertTrue(GuideBookContent.pages().stream().anyMatch(page -> page.title().equals("Loops")), "expected a loop chapter");
        assertTrue(GuideBookContent.pages().stream().anyMatch(page -> page.title().equals("Targeted Screens")), "expected a targeted screens chapter");
        assertTrue(GuideBookContent.pages().stream().flatMap(page -> page.blocks().stream()).anyMatch(block -> block.kind() == GuideBookContent.BlockKind.CODE),
                "expected at least one code block");
        assertTrue(GuideBookContent.pages().stream()
                        .flatMap(page -> page.blocks().stream())
                        .flatMap(block -> block.lines().stream())
                        .anyMatch(line -> line.contains("yield from repeat")),
                "expected the guide to mention the cooperative beginner loop helper");
        assertTrue(GuideBookContent.pages().stream()
                .flatMap(page -> page.blocks().stream())
                .flatMap(block -> block.lines().stream())
                .anyMatch(line -> line.contains("left_panel = get_device")),
            "expected the guide to include a named multi-screen example");
    }

    @Test
    void guideBookSummaryAndBlocksAreNotEmpty() {
        assertFalse(GuideBookContent.pages().isEmpty(), "expected at least one guide page");
        assertTrue(GuideBookContent.pages().stream().allMatch(page -> !page.summary().isBlank()), "expected each guide page to have a summary");
        assertTrue(GuideBookContent.pages().stream().allMatch(page -> !page.blocks().isEmpty()), "expected each guide page to contain content blocks");
    }
}