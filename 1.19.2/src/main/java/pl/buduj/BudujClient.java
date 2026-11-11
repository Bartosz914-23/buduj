package pl.buduj;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public class BudujClient implements ClientModInitializer {
    static KeyBinding LEFT, RIGHT, FWD, BACK, UP, DOWN, ROT, BUILD, CANCEL;
    public static Project active;
    public static int rot;
    public static BlockPos origin = BlockPos.ORIGIN;
    static boolean openRequested;
    static int ticks;
    static final int[] held = new int[6];

    private static KeyBinding key(String n, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buduj." + n, InputUtil.Type.KEYSYM, code, "key.categories.buduj"));
    }

    @Override
    public void onInitializeClient() {
        LEFT = key("left", GLFW.GLFW_KEY_LEFT);
        RIGHT = key("right", GLFW.GLFW_KEY_RIGHT);
        FWD = key("fwd", GLFW.GLFW_KEY_UP);
        BACK = key("back", GLFW.GLFW_KEY_DOWN);
        UP = key("up", GLFW.GLFW_KEY_PAGE_UP);
        DOWN = key("down", GLFW.GLFW_KEY_PAGE_DOWN);
        ROT = key("rot", GLFW.GLFW_KEY_R);
        BUILD = key("build", GLFW.GLFW_KEY_ENTER);
        CANCEL = key("cancel", GLFW.GLFW_KEY_BACKSPACE);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("buduj")
                .executes(c -> { openRequested = true; return 1; })
                .then(ClientCommandManager.literal("cofnij").executes(c -> { undo(); return 1; }))));

        ClientTickEvents.END_CLIENT_TICK.register(BudujClient::tick);
        WorldRenderEvents.LAST.register(Hologram::render);
        ServerTickEvents.END_SERVER_TICK.register(s -> BuildJob.tick());
    }

    public static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    public static void activate(Project p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        active = p;
        rot = 0;
        Direction f = mc.player.getHorizontalFacing();
        BlockPos pp = mc.player.getBlockPos();
        Project.Grid g = p.grid(0);
        BlockPos c = pp.offset(f, 3 + Math.max(g.sx, g.sz) / 2);
        origin = new BlockPos(c.getX() - g.sx / 2, pp.getY(), c.getZ() - g.sz / 2);
        msg("Hologram: strzalki = ruch, PgUp/PgDn = gora/dol, R = obrot, ENTER = BUDUJ, Backspace = anuluj");
    }

    private static void move(Direction d) { origin = origin.offset(d); }

    private static void step(KeyBinding k, Direction d, int idx) {
        if (k.wasPressed()) { move(d); held[idx] = 0; }
        else if (k.isPressed()) { if (++held[idx] > 10 && held[idx] % 2 == 0) move(d); }
        else held[idx] = 0;
    }

    private static void tick(MinecraftClient mc) {
        if (openRequested) {
            openRequested = false;
            mc.setScreen(new ProjectScreen()); // dopiero po zamknieciu czatu
            return;
        }
        if (active == null || mc.player == null || mc.currentScreen != null) return;
        Direction f = mc.player.getHorizontalFacing(), r = f.rotateYClockwise();
        step(LEFT, r.getOpposite(), 0);
        step(RIGHT, r, 1);
        step(FWD, f, 2);
        step(BACK, f.getOpposite(), 3);
        step(UP, Direction.UP, 4);
        step(DOWN, Direction.DOWN, 5);
        if (ROT.wasPressed()) rot = (rot + 1) & 3;
        if (CANCEL.wasPressed()) { active = null; msg("Anulowano hologram."); return; }
        if (BUILD.wasPressed()) { build(mc); return; }
        if (++ticks % 80 == 0) msg("ENTER = BUDUJ | strzalki = ruch | R = obrot | Backspace = anuluj");
    }

    private static void build(MinecraftClient mc) {
        IntegratedServer srv = mc.getServer();
        if (mc.world == null || mc.player == null) return;
        if (srv == null) { // serwer: uruchamiamy funkcje z datapacku (wymaga op i datapacku Buduj na serwerze)
            String cmd = "execute positioned " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                + " run function buduj:" + active.slug + "_" + rot;
            mc.player.sendCommand(cmd, null);
            msg("Wyslano na serwer. Wymagane: op + datapack Buduj na serwerze.");
            active = null;
            return;
        }
        final Project p = active;
        final Project.Grid g = p.grid(rot);
        final BlockPos o = origin;
        final int rr = rot;
        final RegistryKey<World> key = mc.world.getRegistryKey();
        srv.execute(() -> {
            BlockState[] st = new BlockState[p.blocks.length];
            BlockRotation[] R = {BlockRotation.NONE, BlockRotation.CLOCKWISE_90, BlockRotation.CLOCKWISE_180, BlockRotation.COUNTERCLOCKWISE_90};
            for (int i = 0; i < st.length; i++) st[i] = parse(p.blocks[i]).rotate(R[rr]);
            BuildJob.start(srv.getWorld(key), o, g, st, () -> mc.execute(() -> msg("Gotowe! Cofniecie: /buduj cofnij")));
        });
        msg("Buduje...");
        active = null;
    }

    private static void undo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        IntegratedServer srv = mc.getServer();
        if (srv == null) { msg("Cofanie dziala tylko w swiecie jednoosobowym (na serwerze: /undo lub backup)."); return; }
        srv.execute(() -> BuildJob.startUndo(() -> mc.execute(() -> msg("Nie ma czego cofac.")),
                                             () -> mc.execute(() -> msg("Cofnieto budowle."))));
    }

    static BlockState parse(String s) {
        String id = s, props = null;
        int i = s.indexOf('[');
        if (i >= 0) { id = s.substring(0, i); props = s.substring(i + 1, s.length() - 1); }
        Identifier idn = Identifier.tryParse(id);
        Block b = idn == null ? Blocks.AIR : Registry.BLOCK.get(idn);
        BlockState st = b.getDefaultState();
        if (props != null)
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                if (p.length != 2) continue;
                Property<?> pr = b.getStateManager().getProperty(p[0].trim());
                if (pr != null) st = with(st, pr, p[1].trim());
            }
        return st;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st, Property<T> pr, String v) {
        return pr.parse(v).map(x -> st.with(pr, x)).orElse(st);
    }
}
