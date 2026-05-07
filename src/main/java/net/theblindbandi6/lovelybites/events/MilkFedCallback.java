package net.theblindbandi6.lovelybites.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface MilkFedCallback {
    Event<MilkFedCallback> EVENT = EventFactory.createArrayBacked(MilkFedCallback.class,
            (listeners) -> (feeder, targetPlayer, itemStack) -> {

                for (MilkFedCallback listener : listeners) {
                    InteractionResult result =  listener.onFed(feeder, targetPlayer, itemStack);
                    if (result != null) {
                        return result;
                    }
                }

                return null;
            }
    );

    @Nullable InteractionResult onFed(Player feedingPlayer, Player targetPlayer, ItemStack itemStack);
}
