package com.linggango.easyparry;

import com.linggango.easyparry.client.EasyParryClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import top.theillusivec4.curios.api.CuriosApi;

@Mod(EasyParryMod.MOD_ID)
public class EasyParryMod {
    public static final String MOD_ID = "linggango_easy_parry";

    public EasyParryMod() {
        ModLoadingContext.get().registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(
                        () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                        (remote, isServer) -> true
                )
        );

        EasyParryNetwork.init();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EasyParryConfig.SPEC, "linggango_easy_parry-common.toml");

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onConfigLoad);
        modBus.addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.register(new EasyParryServerHandler());

        try {
            EasyParryClient.init(modBus);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isAnyAccessory(ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return false;
        }
        try {
            if (CuriosApi.getCurio(stack).isPresent()) {
                return true;
            }
            if (stack.m_204131_().anyMatch(tag -> "curios".equals(tag.f_203868_().m_135827_()))) {
                return true;
            }
            if (stack.m_41782_() && stack.m_41783_() != null && stack.m_41783_().m_128425_("CurioAttributeModifiers", 9)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            EasyParryConfig.syncToLinggangoTweaks();
            try {
                CuriosApi.registerCurioPredicate(
                        new ResourceLocation(MOD_ID, "any_accessory"),
                        slotResult -> isAnyAccessory(slotResult.stack())
                );
            } catch (Throwable ignored) {
            }
        });
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == EasyParryConfig.SPEC) {
            EasyParryConfig.syncToLinggangoTweaks();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == EasyParryConfig.SPEC) {
            EasyParryConfig.syncToLinggangoTweaks();
            EasyParryNetwork.sendLocalSettingsToServer();
        }
    }
}
