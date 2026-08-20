package org.evgenium.speedrun.client.mcsr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.evgenium.speedrun.client.match.RaceSession;

import java.util.List;

/** Paged view of the last 50 competitive RNG events, newest first. */
public final class McsrRngHistoryScreen extends Screen {
    private static final int PAGE_SIZE = 12;

    private int page;
    private Button previous;
    private Button next;

    public McsrRngHistoryScreen() {
        super(Component.literal("MCSR RNG History"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        previous = Button.builder(Component.literal("<"), button -> {
                if (page > 0) {
                    page--;
                }
            })
            .bounds(centerX - 92, this.height - 30, 40, 20)
            .build();
        next = Button.builder(Component.literal(">"), button -> page++)
            .bounds(centerX + 52, this.height - 30, 40, 20)
            .build();

        this.addRenderableWidget(previous);
        this.addRenderableWidget(next);
        this.addRenderableWidget(Button.builder(Component.literal("ЗАКРЫТЬ"), button -> onClose())
            .bounds(centerX - 48, this.height - 30, 96, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        List<RngEvent> events = CompetitiveRng.historySnapshot();
        int pageCount = Math.max(1, (events.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pageCount - 1));
        if (previous != null) {
            previous.active = page > 0;
        }
        if (next != null) {
            next.active = page + 1 < pageCount;
        }

        String title = "MCSR RNG HISTORY — последние " + CompetitiveRng.HISTORY_LIMIT;
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, 16, 0xFFFFDD77, true);

        String meta = "Seed: " + RaceSession.rngSeed()
            + " • " + RaceSession.randomizationType().displayName()
            + " • событий: " + CompetitiveRng.totalEvents()
            + " • страница " + (page + 1) + "/" + pageCount;
        graphics.text(this.font, meta, (this.width - this.font.width(meta)) / 2, 32, 0xFFBBBBBB, false);

        if (events.isEmpty()) {
            String empty = "Событий пока нет";
            graphics.text(this.font, empty, (this.width - this.font.width(empty)) / 2, 64, 0xFFFFFFFF, false);
            return;
        }

        int newestOffset = page * PAGE_SIZE;
        int y = 54;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = events.size() - 1 - newestOffset - row;
            if (index < 0) {
                break;
            }
            RngEvent event = events.get(index);
            String line = event.compactLine() + " | raw=" + event.rawValue();
            if (line.length() > 180) {
                line = line.substring(0, 177) + "...";
            }
            graphics.text(this.font, line, 14, y, 0xFFFFFFFF, false);
            y += 14;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(null);
    }
}
