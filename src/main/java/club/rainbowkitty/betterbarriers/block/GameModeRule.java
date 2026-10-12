package club.rainbowkitty.betterbarriers.block;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Whether a gamemode barrier lets players in its mode through, or everyone else. */
public enum GameModeRule implements StringRepresentable {
    ALLOW("allow"),
    BLOCK("block");

    public static final Codec<GameModeRule> CODEC =
            StringRepresentable.fromEnum(GameModeRule::values);

    private final String name;

    GameModeRule(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** The other rule. */
    public GameModeRule flipped() {
        return this == ALLOW ? BLOCK : ALLOW;
    }
}
