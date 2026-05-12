package de.xllogic.client.screen;

import de.xllogic.client.guide.GuideBookContent;
import de.xllogic.client.guide.GuideBookContent.Block;
import de.xllogic.client.guide.GuideBookContent.BlockKind;
import de.xllogic.client.guide.GuideBookContent.Page;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class GuideBookScreen extends Screen {
    private static final int BACKDROP = 0xB0080B10;
    private static final int PANEL_BACKGROUND = 0xE60B0F14;
    private static final int PANEL_BORDER = 0xAA2F3A4A;
    private static final int TITLE_COLOR = 0xFFE6EDF3;
    private static final int TEXT_COLOR = 0xFFD9E1EA;
    private static final int INFO_COLOR = 0xFF8B949E;
    private static final int NOTE_COLOR = 0xFF89D185;
    private static final int CODE_TEXT = 0xFFF2E8B6;
    private static final int CODE_BACKGROUND = 0xCC111823;
    private static final int SECTION_BUTTON_WIDTH = 122;
    private static final int SECTION_BUTTON_HEIGHT = 20;
    private static final int SECTION_BUTTON_SPACING = 4;

    private final Screen returnScreen;
    private final List<Page> pages = GuideBookContent.pages();
    private final List<Button> pageButtons = new ArrayList<>();
    private Button previousButton;
    private Button nextButton;
    private int pageIndex;
    private int contentScroll;

    public GuideBookScreen() {
        this(null);
    }

    public GuideBookScreen(final Screen returnScreen) {
        super(Component.translatable("screen.xllogic.guide_book.title"));
        this.returnScreen = returnScreen;
    }

    public PythonComputerScreen returnComputerScreen() {
        return this.returnScreen instanceof PythonComputerScreen computerScreen ? computerScreen : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.returnComputerScreen() instanceof PythonComputerScreen computerScreen) {
            computerScreen.tickOverlayHost();
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.pageButtons.clear();

        final Layout layout = this.layout();
        int buttonY = layout.top() + 34;
        for (int index = 0; index < this.pages.size(); index++) {
            final int targetIndex = index;
            final String label = this.font.plainSubstrByWidth(this.pages.get(index).title(), SECTION_BUTTON_WIDTH - 10);
            final Button button = Button.builder(Component.literal(label), ignored -> this.setPage(targetIndex))
                    .bounds(layout.navLeft() + 8, buttonY, SECTION_BUTTON_WIDTH, SECTION_BUTTON_HEIGHT)
                    .build();
            this.pageButtons.add(this.addRenderableWidget(button));
            buttonY += SECTION_BUTTON_HEIGHT + SECTION_BUTTON_SPACING;
        }

        this.previousButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.xllogic.guide_book.previous"), ignored -> this.previousPage())
                .bounds(layout.contentLeft() + 8, layout.bottom() - 28, 70, 20)
                .build());
        this.nextButton = this.addRenderableWidget(Button.builder(Component.translatable("screen.xllogic.guide_book.next"), ignored -> this.nextPage())
                .bounds(layout.contentRight() - 148, layout.bottom() - 28, 70, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.xllogic.guide_book.done"), ignored -> this.onClose())
                .bounds(layout.contentRight() - 74, layout.bottom() - 28, 66, 20)
                .build());
        this.updateControls();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            this.previousPage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.nextPage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.scrollBy(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.scrollBy(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            this.contentScroll = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            this.contentScroll = this.maxContentScroll(this.layout(), this.currentLines(this.layout()));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        final int direction = (int) Math.signum(scrollY);
        if (direction == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        final Layout layout = this.layout();
        if (!layout.contentContains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        this.scrollBy(-direction);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.returnScreen != null) {
            this.minecraft.setScreen(this.returnScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        final Layout layout = this.layout();
        graphics.fill(0, 0, this.width, this.height, BACKDROP);
        this.drawPanel(graphics, layout.navLeft(), layout.top(), layout.navWidth(), layout.height());
        this.drawPanel(graphics, layout.contentLeft(), layout.top(), layout.contentWidth(), layout.height());

        graphics.drawCenteredString(this.font, this.title, layout.contentCenterX(), layout.top() + 10, TITLE_COLOR);
        graphics.drawString(this.font, Component.translatable("screen.xllogic.guide_book.contents"), layout.navLeft() + 8, layout.top() + 10, TITLE_COLOR, false);

        final Page currentPage = this.pages.get(this.pageIndex);
        graphics.drawString(this.font, Component.literal(currentPage.title()), layout.contentLeft() + 10, layout.top() + 28, TITLE_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable("screen.xllogic.guide_book.page", this.pageIndex + 1, this.pages.size()),
                layout.contentRight() - 82,
                layout.top() + 28,
                INFO_COLOR,
                false);
        graphics.drawString(this.font,
                Component.translatable("screen.xllogic.guide_book.controls"),
                layout.contentLeft() + 10,
                layout.bottom() - 42,
                INFO_COLOR,
                false);

        final List<RenderedLine> lines = this.currentLines(layout);
        final int availableHeight = layout.contentHeight() - 78;
        final int lineHeight = this.font.lineHeight + 2;
        final int visibleLines = Math.max(1, availableHeight / lineHeight);
        final int maxScroll = this.maxContentScroll(layout, lines);
        this.contentScroll = Mth.clamp(this.contentScroll, 0, maxScroll);

        int y = layout.top() + 48;
        for (int index = this.contentScroll; index < Math.min(lines.size(), this.contentScroll + visibleLines); index++) {
            final RenderedLine line = lines.get(index);
            if (line.backgroundColor() != 0) {
                graphics.fill(layout.contentLeft() + 8, y - 1, layout.contentRight() - 8, y + this.font.lineHeight + 1, line.backgroundColor());
            }
            graphics.drawString(this.font, line.text(), layout.contentLeft() + 12 + line.indent(), y, line.color(), false);
            y += lineHeight;
        }

        if (maxScroll > 0) {
            final String scrollLabel = this.contentScroll >= maxScroll
                    ? this.getTranslatedOrLiteral("screen.xllogic.guide_book.scroll_bottom", "Bottom of page")
                    : this.getTranslatedOrLiteral("screen.xllogic.guide_book.scroll_more", "Mouse wheel scrolls for more") ;
            graphics.drawString(this.font, scrollLabel, layout.contentRight() - this.font.width(scrollLabel) - 10, layout.bottom() - 42, INFO_COLOR, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(final GuiGraphics graphics, final int left, final int top, final int width, final int height) {
        graphics.fill(left, top, left + width, top + height, PANEL_BACKGROUND);
        graphics.hLine(left, left + width - 1, top, PANEL_BORDER);
        graphics.hLine(left, left + width - 1, top + height - 1, PANEL_BORDER);
        graphics.vLine(left, top, top + height - 1, PANEL_BORDER);
        graphics.vLine(left + width - 1, top, top + height - 1, PANEL_BORDER);
    }

    private List<RenderedLine> currentLines(final Layout layout) {
        final Page page = this.pages.get(this.pageIndex);
        final int wrapWidth = Math.max(80, layout.contentWidth() - 30);
        final ArrayList<RenderedLine> lines = new ArrayList<>();

        this.appendWrapped(lines, page.summary(), wrapWidth, TEXT_COLOR, 0, 0);
        lines.add(RenderedLine.blank());
        for (final Block block : page.blocks()) {
            switch (block.kind()) {
                case TEXT -> {
                    for (final String paragraph : block.lines()) {
                        this.appendWrapped(lines, paragraph, wrapWidth, TEXT_COLOR, 0, 0);
                        lines.add(RenderedLine.blank());
                    }
                }
                case BULLETS -> {
                    for (final String bullet : block.lines()) {
                        this.appendBullet(lines, bullet, wrapWidth);
                    }
                    lines.add(RenderedLine.blank());
                }
                case CODE -> {
                    for (final String codeLine : block.lines()) {
                        this.appendWrapped(lines, codeLine, wrapWidth - 8, CODE_TEXT, CODE_BACKGROUND, 6);
                    }
                    lines.add(RenderedLine.blank());
                }
                case NOTE -> {
                    for (final String paragraph : block.lines()) {
                        this.appendWrapped(lines, paragraph, wrapWidth, NOTE_COLOR, 0, 0);
                        lines.add(RenderedLine.blank());
                    }
                }
            }
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).text().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private void appendBullet(final List<RenderedLine> lines, final String text, final int width) {
        final List<String> wrapped = this.wrapText(text, Math.max(40, width - 12));
        for (int index = 0; index < wrapped.size(); index++) {
            final String prefix = index == 0 ? "- " : "  ";
            lines.add(new RenderedLine(prefix + wrapped.get(index), TEXT_COLOR, 0, 0));
        }
        lines.add(RenderedLine.blank());
    }

    private void appendWrapped(final List<RenderedLine> lines, final String text, final int width, final int color, final int backgroundColor, final int indent) {
        if (text == null || text.isEmpty()) {
            lines.add(new RenderedLine("", color, backgroundColor, indent));
            return;
        }

        for (final String wrapped : this.wrapText(text, width)) {
            lines.add(new RenderedLine(wrapped, color, backgroundColor, indent));
        }
    }

    private List<String> wrapText(final String text, final int width) {
        final ArrayList<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String remaining = text;
        while (!remaining.isEmpty()) {
            if (this.font.width(remaining) <= width) {
                lines.add(remaining);
                break;
            }

            String candidate = this.font.plainSubstrByWidth(remaining, width);
            if (candidate.isEmpty()) {
                lines.add(remaining);
                break;
            }

            if (!candidate.equals(remaining)) {
                final int breakAt = candidate.lastIndexOf(' ');
                if (breakAt > 8) {
                    candidate = candidate.substring(0, breakAt);
                }
            }

            lines.add(candidate.stripTrailing());
            remaining = remaining.substring(Math.min(remaining.length(), candidate.length())).stripLeading();
        }
        return lines;
    }

    private int maxContentScroll(final Layout layout, final List<RenderedLine> lines) {
        final int availableHeight = layout.contentHeight() - 78;
        final int visibleLines = Math.max(1, availableHeight / (this.font.lineHeight + 2));
        return Math.max(0, lines.size() - visibleLines);
    }

    private void scrollBy(final int delta) {
        final Layout layout = this.layout();
        final int maxScroll = this.maxContentScroll(layout, this.currentLines(layout));
        this.contentScroll = Mth.clamp(this.contentScroll + delta, 0, maxScroll);
    }

    private void previousPage() {
        this.setPage(this.pageIndex - 1);
    }

    private void nextPage() {
        this.setPage(this.pageIndex + 1);
    }

    private void setPage(final int targetIndex) {
        final int clamped = Mth.clamp(targetIndex, 0, this.pages.size() - 1);
        if (clamped == this.pageIndex) {
            return;
        }
        this.pageIndex = clamped;
        this.contentScroll = 0;
        this.updateControls();
    }

    private void updateControls() {
        for (int index = 0; index < this.pageButtons.size(); index++) {
            this.pageButtons.get(index).active = index != this.pageIndex;
        }
        if (this.previousButton != null) {
            this.previousButton.active = this.pageIndex > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = this.pageIndex < this.pages.size() - 1;
        }
    }

    private String getTranslatedOrLiteral(final String key, final String fallback) {
        final Component translated = Component.translatable(key);
        final String value = translated.getString();
        return value.equals(key) ? fallback : value;
    }

    private Layout layout() {
        final int panelWidth = Math.min(this.width - 24, 492);
        final int panelHeight = Math.min(this.height - 24, 300);
        final int left = (this.width - panelWidth) / 2;
        final int top = Math.max(12, (this.height - panelHeight) / 2);
        final int navWidth = 140;
        return new Layout(left, top, panelWidth, panelHeight, navWidth);
    }

    private record Layout(int left, int top, int width, int height, int navWidth) {
        int navLeft() {
            return this.left;
        }

        int contentLeft() {
            return this.left + this.navWidth + 8;
        }

        int contentWidth() {
            return this.width - this.navWidth - 8;
        }

        int contentHeight() {
            return this.height;
        }

        int contentRight() {
            return this.contentLeft() + this.contentWidth();
        }

        int contentCenterX() {
            return this.contentLeft() + this.contentWidth() / 2;
        }

        int bottom() {
            return this.top + this.height;
        }

        boolean contentContains(final double mouseX, final double mouseY) {
            return mouseX >= this.contentLeft() && mouseX < this.contentRight()
                    && mouseY >= this.top && mouseY < this.bottom();
        }
    }

    private record RenderedLine(String text, int color, int backgroundColor, int indent) {
        private static RenderedLine blank() {
            return new RenderedLine("", TEXT_COLOR, 0, 0);
        }
    }
}