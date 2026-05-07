package net.theblindbandi6.lovelybites.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.theblindbandi6.lovelybites.advancement.ModCriteria;
import net.theblindbandi6.lovelybites.events.PlayerFedCallback;
import net.theblindbandi6.lovelybites.util.ModStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;


@Mixin(Player.class)
public abstract class PlayerMixin extends Avatar implements ContainerUser {
    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    public InteractionResult interactOn(final Entity entity, final InteractionHand hand, final Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {

        //Check for offhand
        if (hand == InteractionHand.OFF_HAND && entity instanceof Player targetPlayer) {

            //Get feeding player's offhand items
            Player feedingPlayer = (Player) (Object) this;
            ItemStack itemStack = feedingPlayer.getItemInHand(hand);

            //Check for itemStack cooldown
            if (!feedingPlayer.getCooldowns().isOnCooldown(itemStack)) {

                /* FOOD LOGIC */

                //Check for FOOD data component in item stack
                FoodProperties food = itemStack.get(DataComponents.FOOD);
                if (food != null) {

                    //Check target player's food levels and item stack cooldown
                    int playerHunger = targetPlayer.getFoodData().getFoodLevel();
                    if ((playerHunger < 20 || food.canAlwaysEat())) {

                        //Event Callback for Food
                        InteractionResult eventResult = PlayerFedCallback.EVENT.invoker().onFed(feedingPlayer, targetPlayer, itemStack);
                        if (eventResult != null) {
                            cir.setReturnValue(InteractionResult.PASS);
                            cir.cancel();
                        }

                        //Feed target player
                        int hunger = food.nutrition();
                        float saturation = food.saturation();
                        targetPlayer.getFoodData().eat(hunger, saturation);

                        //Statistic increment
                        feedingPlayer.awardStat(ModStats.FOOD_FED_TO_PLAYERS);

                        //Advancement Trigger
                        if (feedingPlayer instanceof ServerPlayer) {
                            ModCriteria.FOOD_FED_TO_PLAYER.trigger((ServerPlayer) feedingPlayer);
                        }

                        //Check for consumable component for Potion Effects
                        Level level = targetPlayer.level();
                        Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
                        if (consumable != null) {
                            List<ConsumeEffect> effects = consumable.onConsumeEffects();
                            effects.forEach(action -> action.apply(level, itemStack, targetPlayer));
                        }

                        //Play eating effect and send particles
                        BlockPos pos = targetPlayer.blockPosition();
                        level.playSound(null, pos, SoundEvents.FOX_EAT, SoundSource.PLAYERS, 1.0F, 1.0F);
                        if (targetPlayer.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.HEART, targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getBbHeight() * 0.9, targetPlayer.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
                        }

                        //Check for survival
                        if (!feedingPlayer.isCreative()) {

                            //Give leftovers e.g. Bowl, Bottle
                            int stackCount = itemStack.getCount();
                            UseRemainder leftover = itemStack.get(DataComponents.USE_REMAINDER);
                            if (leftover != null) {
                                ItemStack newHandStack = leftover.convertIntoRemainder(itemStack, stackCount, feedingPlayer.hasInfiniteMaterials(), feedingPlayer::handleExtraItemsCreatedOnUse);
                                feedingPlayer.setItemInHand(hand, newHandStack);
                            }

                            //Set cooldown
                            if (consumable != null) {
                                int cooldown = consumable.consumeTicks();
                                feedingPlayer.getCooldowns().addCooldown(itemStack, cooldown);
                            } else {
                                feedingPlayer.getCooldowns().addCooldown(itemStack, 32);
                            }

                            itemStack.consume(1, feedingPlayer);
                        }

                        cir.setReturnValue(InteractionResult.SUCCESS);

                    } else {

                        //Send message to feeding player if target player is full
                        feedingPlayer.sendOverlayMessage(Component.translatable("entity.lovely_bites.player.not_hungry"));
                        cir.setReturnValue(InteractionResult.PASS);

                    }
                }

                /* POTION LOGIC */

                //Check for POTION_CONTENTS data component in item stack
                PotionContents potionContents = itemStack.get(DataComponents.POTION_CONTENTS);
                if (potionContents != null) {

                    //Event Callback for Potions

                    //Apply Potion Effects
                    float durationScale = itemStack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
                    potionContents.forEachEffect(effect -> targetPlayer.addEffect(effect, feedingPlayer), durationScale);

                    //Statistic increment
                    feedingPlayer.awardStat(ModStats.POTIONS_FED_TO_PLAYERS);

                    //Advancement Trigger
                    if (feedingPlayer instanceof ServerPlayer) {
                        ModCriteria.POTION_FED_TO_PLAYER.trigger((ServerPlayer) feedingPlayer);
                    }

                    //Play eating effect and send particles
                    BlockPos pos = targetPlayer.blockPosition();
                    Level level = targetPlayer.level();
                    level.playSound(null, pos, SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    if (targetPlayer.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.HEART, targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getBbHeight() * 0.9, targetPlayer.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
                    }

                    //Check for survival
                    if (!feedingPlayer.isCreative()) {
                        feedingPlayer.addItem(Items.GLASS_BOTTLE.getDefaultInstance());
                        feedingPlayer.getCooldowns().addCooldown(itemStack, 32);
                        itemStack.consume(1, feedingPlayer);
                    }

                    cir.setReturnValue(InteractionResult.SUCCESS);

                }

                /* MILK BUCKET LOGIC */

                if (itemStack.is(Items.MILK_BUCKET)) {

                    //Event Callback for Milk Buckets

                    //Remove all potion effects
                    targetPlayer.removeAllEffects();

                    //Statistic increment
                    feedingPlayer.awardStat(ModStats.MILK_FED_TO_PLAYERS);

                    //Advancement Trigger
                    if (feedingPlayer instanceof ServerPlayer) {
                        ModCriteria.MILK_FED_TO_PLAYER.trigger((ServerPlayer) feedingPlayer);
                    }

                    //Play eating effect and send particles
                    BlockPos pos = targetPlayer.blockPosition();
                    Level level = targetPlayer.level();
                    level.playSound(null, pos, SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
                    if (targetPlayer.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.HEART, targetPlayer.getX(), targetPlayer.getY() + targetPlayer.getBbHeight() * 0.9, targetPlayer.getZ(), 3, 0.3, 0.2, 0.3, 0.02);
                    }

                    //Check for survival
                    if (!feedingPlayer.isCreative()) {
                        feedingPlayer.addItem(Items.BUCKET.getDefaultInstance());
                        feedingPlayer.getCooldowns().addCooldown(itemStack, 32);
                        itemStack.consume(1, feedingPlayer);
                    }

                    cir.setReturnValue(InteractionResult.SUCCESS);

                }
            }
        }
        return InteractionResult.PASS;
    }
}
