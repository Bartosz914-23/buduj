package pl.buduj;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Stawia budowle partiami (kilka tysiecy blokow na tick), od dolu do gory. Pamieta stary stan do cofniecia. */
public class BuildJob {
    private static final List<BuildJob> JOBS = new ArrayList<>();
    static BuildJob last;
    private static final int PER_TICK = 1200;

    final ServerWorld world;
    final BlockPos origin;
    final Project.Grid g;
    final BlockState[] states;
    final BlockState[] old;
    final boolean undo;
    final Runnable done;
    int pos = 0;

    private BuildJob(ServerWorld w, BlockPos o, Project.Grid g, BlockState[] st, BlockState[] old, boolean undo, Runnable done) {
        this.world = w; this.origin = o; this.g = g; this.states = st; this.old = old; this.undo = undo; this.done = done;
    }

    public static void start(ServerWorld w, BlockPos o, Project.Grid g, BlockState[] st, Runnable done) {
        JOBS.add(new BuildJob(w, o, g, st, new BlockState[g.d.length], false, done));
    }

    public static void startUndo(Runnable noneMsg, Runnable done) {
        BuildJob j = last;
        if (j == null) { noneMsg.run(); return; }
        last = null;
        JOBS.add(new BuildJob(j.world, j.origin, j.g, j.states, j.old, true, done));
    }

    public static void tick() {
        Iterator<BuildJob> it = JOBS.iterator();
        while (it.hasNext()) {
            BuildJob j = it.next();
            if (j.step()) {
                it.remove();
                if (!j.undo) last = j;
                j.done.run();
            }
        }
    }

    private boolean step() {
        int plane = g.sx * g.sz, n = 0;
        while (pos < g.d.length && n < PER_TICK) {
            int i = pos++, v = g.d[i];
            if (v == 0) continue;
            BlockPos p = origin.add(i % g.sx, i / plane, (i / g.sx) % g.sz);
            if (undo) {
                if (old[i] != null) world.setBlockState(p, old[i], Block.NOTIFY_ALL);
            } else {
                old[i] = world.getBlockState(p);
                world.setBlockState(p, states[v - 1], Block.NOTIFY_ALL);
            }
            n++;
        }
        return pos >= g.d.length;
    }

    static BlockState air() { return Blocks.AIR.getDefaultState(); }
}
