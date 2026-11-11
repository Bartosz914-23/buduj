package pl.buduj;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import net.minecraft.util.math.Vec3d;

/** Polprzezroczysty hologram: rysuje tylko widoczne sciany (kolor bloku), bez zapisu do bufora glebi. */
public class Hologram {
    private static Project cp;
    private static int cr = -1;
    private static int[] faces = new int[0];
    private static Project.Grid cg;
    private static final float[] SHADE = {0.85f, 0.75f, 1.0f, 0.6f, 0.9f, 0.7f};

    private static boolean occ(Project p, Project.Grid g, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= g.sx || y >= g.sy || z >= g.sz) return false;
        int v = g.d[(y * g.sz + z) * g.sx + x];
        return v > 0 && p.colors[v - 1] >= 0;
    }

    private static void rebuild(Project p, int r) {
        cp = p; cr = r; cg = p.grid(r);
        int[] cnt = {0};
        int[] tmp = new int[1024];
        for (int y = 0; y < cg.sy; y++)
            for (int z = 0; z < cg.sz; z++)
                for (int x = 0; x < cg.sx; x++) {
                    if (!occ(p, cg, x, y, z)) continue;
                    int i = (y * cg.sz + z) * cg.sx + x;
                    int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int dir = 0; dir < 6; dir++) {
                        if (occ(p, cg, x + nb[dir][0], y + nb[dir][1], z + nb[dir][2])) continue;
                        if (cnt[0] >= tmp.length) tmp = java.util.Arrays.copyOf(tmp, tmp.length * 2);
                        tmp[cnt[0]++] = (i << 3) | dir;
                    }
                }
        faces = java.util.Arrays.copyOf(tmp, cnt[0]);
    }

    private static void v(BufferBuilder bb, Matrix4f m, float x, float y, float z, float r, float g, float b) {
        bb.vertex(m, x, y, z).color(r, g, b, 0.5f);
    }

    public static void render(WorldRenderContext ctx) {
        Project p = BudujClient.active;
        if (p == null) return;
        if (cp != p || cr != BudujClient.rot) rebuild(p, BudujClient.rot);
        Vec3d cam = ctx.camera().getPos();
        int ox = BudujClient.origin.getX(), oy = BudujClient.origin.getY(), oz = BudujClient.origin.getZ();
        double lx = cam.x - ox, ly = cam.y - oy, lz = cam.z - oz;

        MatrixStack ms = ctx.matrixStack();
        ms.push();
        ms.translate(ox - cam.x, oy - cam.y, oz - cam.z);
        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        final float e = 0.01f;
        int[] d = cg.d;
        int plane = cg.sx * cg.sz;
        for (int f : faces) {
            int i = f >> 3, dir = f & 7;
            int x = i % cg.sx, z = (i / cg.sx) % cg.sz, y = i / plane;
            double dx = x - lx, dz = z - lz;
            if (dx * dx + dz * dz > 16384) continue; // dalej niz 128 blokow nie rysujemy
            boolean front = switch (dir) {
                case 0 -> lx > x + 1;
                case 1 -> lx < x;
                case 2 -> ly > y + 1;
                case 3 -> ly < y;
                case 4 -> lz > z + 1;
                default -> lz < z;
            };
            if (!front) continue;
            int c = p.colors[d[i] - 1];
            float s = SHADE[dir];
            float r = ((c >> 16) & 255) / 255f * s, g = ((c >> 8) & 255) / 255f * s, b = (c & 255) / 255f * s;
            float x0 = x + e, x1 = x + 1 - e, y0 = y + e, y1 = y + 1 - e, z0 = z + e, z1 = z + 1 - e;
            switch (dir) {
                case 0 -> { v(bb, m, x1, y0, z0, r, g, b); v(bb, m, x1, y1, z0, r, g, b); v(bb, m, x1, y1, z1, r, g, b); v(bb, m, x1, y0, z1, r, g, b); }
                case 1 -> { v(bb, m, x0, y0, z0, r, g, b); v(bb, m, x0, y1, z0, r, g, b); v(bb, m, x0, y1, z1, r, g, b); v(bb, m, x0, y0, z1, r, g, b); }
                case 2 -> { v(bb, m, x0, y1, z0, r, g, b); v(bb, m, x0, y1, z1, r, g, b); v(bb, m, x1, y1, z1, r, g, b); v(bb, m, x1, y1, z0, r, g, b); }
                case 3 -> { v(bb, m, x0, y0, z0, r, g, b); v(bb, m, x0, y0, z1, r, g, b); v(bb, m, x1, y0, z1, r, g, b); v(bb, m, x1, y0, z0, r, g, b); }
                case 4 -> { v(bb, m, x0, y0, z1, r, g, b); v(bb, m, x1, y0, z1, r, g, b); v(bb, m, x1, y1, z1, r, g, b); v(bb, m, x0, y1, z1, r, g, b); }
                default -> { v(bb, m, x0, y0, z0, r, g, b); v(bb, m, x1, y0, z0, r, g, b); v(bb, m, x1, y1, z0, r, g, b); v(bb, m, x0, y1, z0, r, g, b); }
            }
        }
        BuiltBuffer built = bb.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        ms.pop();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
