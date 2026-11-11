package pl.buduj;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

/** Projekt .buduj: paleta + siatka (indeks palety + 1, 0 = pusto). Kolejnosc: y, z, x. */
public class Project {
    public static class Grid {
        public final int sx, sy, sz;
        public final int[] d;
        public Grid(int sx, int sy, int sz, int[] d) { this.sx = sx; this.sy = sy; this.sz = sz; this.d = d; }
    }

    public String name, slug;
    public String[] blocks;
    public int[] colors; // RGB lub -1 dla powietrza
    private final Grid[] rots = new Grid[4];

    public Grid grid(int r) {
        r &= 3;
        if (rots[r] == null) rots[r] = rot90(grid(r - 1));
        return rots[r];
    }

    /** Obrot o 90 stopni zgodnie z ruchem wskazowek zegara (patrzac z gory): (x,z) -> (sz-1-z, x). */
    private static Grid rot90(Grid g) {
        int nsx = g.sz, nsz = g.sx;
        int[] nd = new int[g.d.length];
        for (int y = 0; y < g.sy; y++)
            for (int z = 0; z < g.sz; z++)
                for (int x = 0; x < g.sx; x++)
                    nd[(y * nsz + x) * nsx + (g.sz - 1 - z)] = g.d[(y * g.sz + z) * g.sx + x];
        return new Grid(nsx, g.sy, nsz, nd);
    }

    public static Project load(Path path) throws IOException {
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(path)), StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            Project p = new Project();
            p.name = o.get("name").getAsString();
            p.slug = o.has("slug") ? o.get("slug").getAsString() : "budowla";
            JsonArray size = o.getAsJsonArray("size");
            int sx = size.get(0).getAsInt(), sy = size.get(1).getAsInt(), sz = size.get(2).getAsInt();
            JsonArray pal = o.getAsJsonArray("palette");
            p.blocks = new String[pal.size()];
            p.colors = new int[pal.size()];
            for (int i = 0; i < pal.size(); i++) {
                JsonObject e = pal.get(i).getAsJsonObject();
                p.blocks[i] = e.get("b").getAsString();
                JsonElement c = e.get("c");
                p.colors[i] = (c == null || c.isJsonNull()) ? -1 : Integer.parseInt(c.getAsString(), 16);
            }
            int[] d = new int[sx * sy * sz];
            JsonArray rle = o.getAsJsonArray("rle");
            int pos = 0;
            for (int i = 0; i + 1 < rle.size(); i += 2) {
                int v = rle.get(i).getAsInt(), n = rle.get(i + 1).getAsInt();
                for (int k = 0; k < n && pos < d.length; k++) d[pos++] = v;
            }
            p.rots[0] = new Grid(sx, sy, sz, d);
            return p;
        }
    }
}
