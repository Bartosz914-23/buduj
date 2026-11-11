package pl.buduj;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Lista zapisanych projektow (.minecraft/buduj_projects). */
public class ProjectScreen extends Screen {
    private static final int PER = 6;
    private List<Path> files = new ArrayList<>();
    private int page = 0;

    public ProjectScreen() { this(0); }
    public ProjectScreen(int page) { super(Text.literal("Buduj - zapisane projekty")); this.page = page; }

    public static Path folder() { return FabricLoader.getInstance().getGameDir().resolve("buduj_projects"); }

    @Override
    protected void init() {
        files.clear();
        try {
            Files.createDirectories(folder());
            try (Stream<Path> s = Files.list(folder())) {
                s.filter(f -> f.toString().endsWith(".buduj"))
                 .sorted(Comparator.comparingLong((Path f) -> f.toFile().lastModified()).reversed())
                 .forEach(files::add);
            }
        } catch (IOException ignored) { }
        int w = 260, x = width / 2 - w / 2, y = height / 2 - 80;
        int pages = Math.max(1, (files.size() + PER - 1) / PER);
        page = Math.min(page, pages - 1);
        for (int k = 0; k < PER; k++) {
            int i = page * PER + k;
            if (i >= files.size()) break;
            Path f = files.get(i);
            String n = f.getFileName().toString().replaceAll("\\.buduj$", "");
            addDrawableChild(new ButtonWidget(x, y + k * 24, w, 20, Text.literal(n), b -> choose(f)));
        }
        if (pages > 1) {
            addDrawableChild(new ButtonWidget(x, y + PER * 24 + 4, 126, 20, Text.literal("< Poprzednia"), b -> client.setScreen(new ProjectScreen((page + pages - 1) % pages))));
            addDrawableChild(new ButtonWidget(x + 134, y + PER * 24 + 4, 126, 20, Text.literal("Nastepna >"), b -> client.setScreen(new ProjectScreen((page + 1) % pages))));
        }
        addDrawableChild(new ButtonWidget(x, y + PER * 24 + 30, w, 20, Text.literal("Zamknij"), b -> close()));
    }

    private void choose(Path f) {
        try {
            Project p = Project.load(f);
            close();
            BudujClient.activate(p);
        } catch (Exception e) {
            BudujClient.msg("Nie mozna wczytac projektu: " + e.getMessage());
        }
    }

    @Override
    public void render(MatrixStack m, int mx, int my, float delta) {
        renderBackground(m);
        drawCenteredText(m, textRenderer, title, width / 2, height / 2 - 110, 0xFFFFFF);
        if (files.isEmpty())
            drawCenteredText(m, textRenderer, Text.literal("Brak projektow. Zapisz je w aplikacji Buduj."), width / 2, height / 2 - 60, 0xAAAAAA);
        super.render(m, mx, my, delta);
    }
}
