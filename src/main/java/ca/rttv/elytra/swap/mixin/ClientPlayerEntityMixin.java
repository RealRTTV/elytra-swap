package ca.rttv.elytra.swap.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin extends PlayerEntity {
    @Shadow @Final
    protected MinecraftClient client;

    public ClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private void tick(CallbackInfo ci) {
        ItemStack elytra = getEquippedStack(EquipmentSlot.CHEST);
        if (elytra.getItem() instanceof ArmorItem && !onGround && !isFallFlying() && !isTouchingWater() && !hasStatusEffect(StatusEffects.LEVITATION)) {
            for (int i = 0; i < getInventory().main.size(); i++) {
                ItemStack stack = getInventory().main.get(i);
                if (stack.getItem() instanceof ElytraItem && ElytraItem.isUsable(stack)) {
                    swapSlots(i);
                    break;
                }
            }
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void elytra(CallbackInfo ci) {
        ItemStack possiblyElytra = getEquippedStack(EquipmentSlot.CHEST);
        if (possiblyElytra.isOf(Items.ELYTRA) && !getFlag(Entity.FALL_FLYING_FLAG_INDEX) && (client.currentScreen == null || client.currentScreen instanceof AbstractInventoryScreen<?>)) {
            for (int i = 0; i < getInventory().main.size(); i++) {
                ItemStack stack = getInventory().main.get(i);
                if (stack.getItem() instanceof ArmorItem armorItem && armorItem.getSlotType() == EquipmentSlot.CHEST) {
                    swapSlots(i);
                    break;
                }
            }
        }
    }

    private void swapSlots(int slot) {
        if (slot > 8) {
            //noinspection ConstantConditions
            client.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, this);
            client.interactionManager.clickSlot(0, 6, 40, SlotActionType.SWAP, this);
            client.interactionManager.clickSlot(0, slot, 40, SlotActionType.SWAP, this);
        } else {
            //noinspection ConstantConditions
            client.interactionManager.clickSlot(0, 6, slot, SlotActionType.SWAP, this);
        }
    }
}
