package ca.rttv.elytra.swap.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin extends PlayerEntity {
    @Shadow @Final
    protected MinecraftClient client;

    @Shadow public Input input;
    @Unique private boolean startedElytra = false;
    @Unique private boolean releasedJumpKey = false;
    @Unique private int ticksUntilHop = 0;

    public ClientPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private void swapToElytra(CallbackInfo ci) {
        ItemStack chestStack = getEquippedStack(EquipmentSlot.CHEST);
        boolean shouldFallFlying = !isOnGround() && !isFallFlying() && !isTouchingWater() && !hasStatusEffect(StatusEffects.LEVITATION) && !isInLava() && !hasVehicle() && !isClimbing();
        if ((chestStack.getItem() instanceof ArmorItem || chestStack.isEmpty()) && shouldFallFlying) {
            if (trySwap(stack -> stack.getItem() instanceof ElytraItem && ElytraItem.isUsable(stack))) {
                releasedJumpKey = false;
                startedElytra = true;
            }
        }
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void swapFromElytra(CallbackInfo ci) {
        if (--ticksUntilHop == 0) {
            startFallFlying();
            client.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        ItemStack chestStack = getEquippedStack(EquipmentSlot.CHEST);
        boolean regularDisable = !isFallFlying() && !hasStatusEffect(StatusEffects.LEVITATION) && !hasVehicle() && !isClimbing();
        if (startedElytra && !releasedJumpKey && input.jumping && isOnGround() && regularDisable) {
            ticksUntilHop = 1 + (getPing() + 24) / 25;
        } else {
            // the "isInLava" call is there for obvious safety
            boolean contactDisable = regularDisable || isInLava();
            boolean jumpDisable = input.jumping && releasedJumpKey;
            boolean brokenDisable = chestStack.isOf(Items.ELYTRA) && !ElytraItem.isUsable(chestStack);
            if (chestStack.isOf(Items.ELYTRA) && (contactDisable || jumpDisable || brokenDisable)) {
                if (trySwap(stack -> stack.getItem() instanceof ArmorItem armorItem && armorItem.getSlotType() == EquipmentSlot.CHEST)) {
                    startedElytra = false;
                }
            }
        }

        if (!input.jumping && chestStack.isOf(Items.ELYTRA)) {
            releasedJumpKey = true;
        }
    }

    @Unique
    private boolean trySwap(Predicate<ItemStack> predicate) {
        for (int i = 0; i < getInventory().main.size(); i++) {
            ItemStack stack = getInventory().main.get(i);
            if (predicate.test(stack)) {
                swapSlots(6, i);
                return true;
            }
        }

        return false;
    }

    @Unique
    private void swapSlots(int a, int b) {
        int syncId = client.currentScreen instanceof HandledScreen<?> handledScreen ? handledScreen.getScreenHandler().syncId : 0;
        client.interactionManager.clickSlot(syncId, a, 0, SlotActionType.PICKUP, this);
        client.interactionManager.clickSlot(syncId, b, 0, SlotActionType.PICKUP, this);
        client.interactionManager.clickSlot(syncId, a, 0, SlotActionType.PICKUP, this);
    }

    @Unique
    private int getPing() {
        return Optional.ofNullable(client.getNetworkHandler().getPlayerListEntry(client.player.getUuid())).map(PlayerListEntry::getLatency).orElse(0);
    }
}
