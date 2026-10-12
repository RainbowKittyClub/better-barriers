package club.rainbowkitty.betterbarriers.block;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import club.rainbowkitty.betterbarriers.BetterBarriers;
import club.rainbowkitty.betterbarriers.mixin.CustomDataAccessor;

/**
 * Locks and keys. A keyed barrier holds one lock name; a key is any item whose custom data lists
 * that name, {@code custom_data={betterbarriers:{locks:["vault"]}}}. Survival play cannot write
 * custom data, so keys come only from commands, datapacks and loot, and an item survival can copy
 * along with its data (a written book, a banner) never counts as one. Names ignore case, for A-Z
 * only: every comparison folds A-Z to a-z and leaves any other character as it is.
 */
public final class BarrierLocks {
    private static final String LOCKS = "locks";
    // Dot-separated segments: a lock name ends a permission node, so `vault.*` covers `vault.east`.
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+(\\.[a-z0-9_-]+)*");
    private static final ListTag NO_NAMES = new ListTag();

    /** A lock name in any case, read back folded; refused unless it then fits the pattern. */
    public static final Codec<String> CODEC = Codec.STRING.comapFlatMap(name -> {
        String lock = fold(name);
        return NAME.matcher(lock).matches()
                ? DataResult.success(lock)
                : DataResult.error(() -> "Not a lock name: " + name);
    }, lock -> lock);

    /** Items survival play can copy along with their data, which so never open a lock. */
    public static final TagKey<Item> COPYABLE =
            TagKey.create(Registries.ITEM, BetterBarriers.id("copyable_in_survival"));

    private BarrierLocks() {
    }

    /** Whether {@code stack} is a key for {@code lock}, a folded lock name. */
    public static boolean opens(ItemStack stack, String lock) {
        ListTag names = namesOf(stack);
        for (int i = 0; i < names.size(); i++) {
            if (foldsTo(names.getStringOr(i, ""), lock)) {
                return true;
            }
        }
        return false;
    }

    /** The lock names {@code stack} is a key for, folded, in order and without repeats. */
    public static List<String> locksOf(ItemStack stack) {
        ListTag names = namesOf(stack);
        Set<String> locks = new LinkedHashSet<>();
        for (int i = 0; i < names.size(); i++) {
            String lock = fold(names.getStringOr(i, ""));
            if (NAME.matcher(lock).matches()) {
                locks.add(lock);
            }
        }
        return List.copyOf(locks);
    }

    // `name` with A-Z as a-z. Not toLowerCase: that and equalsIgnoreCase also fold a few non-ASCII
    // letters (the long s, the dotted capital I), and the two do not agree on which.
    private static String fold(String name) {
        StringBuilder folded = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            folded.append(fold(name.charAt(i)));
        }
        return folded.toString();
    }

    private static char fold(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    // Whether `name` folds to `lock`, a character at a time, so a check makes no new string.
    private static boolean foldsTo(String name, String lock) {
        if (name.length() != lock.length()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (fold(name.charAt(i)) != lock.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // The names the item's custom data lists, read in place; none for a copyable item.
    private static ListTag namesOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || stack.is(COPYABLE)) {
            return NO_NAMES;
        }
        return ((CustomDataAccessor) (Object) data).betterbarriers$tag()
                .getCompoundOrEmpty(BetterBarriers.MOD_ID).getListOrEmpty(LOCKS);
    }
}
