package invtweaks;

import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.inventory.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.*;
import net.minecraft.util.*;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.items.*;

import org.apache.logging.log4j.*;

import com.google.common.base.Throwables;

import invtweaks.gui.*;
import invtweaks.packets.*;
import it.unimi.dsi.fastutil.objects.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import javax.annotation.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(InvTweaksMod.MODID)
public class InvTweaksMod {
	// Directly reference a log4j logger.
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LogManager.getLogger(InvTweaksMod.MODID.toUpperCase(Locale.US));
	
	public static final String MODID = "invtweaks";
	
	public static final String CHANNEL = "channel";
	
	public static final String NET_VERS = "1";
	
	public static SimpleChannel NET_INST;
	
	@Nullable
	private static MinecraftServer server;
	
	public InvTweaksMod() {
		// Register the setup method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		// Register the enqueueIMC method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
		// Register the processIMC method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
		
		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	private void setup(final FMLCommonSetupEvent event) {
		// if client doesn't have mod installed, that's fine
		NET_INST = NetworkRegistry.newSimpleChannel(
				new ResourceLocation(MODID, CHANNEL),
				() -> NET_VERS, NET_VERS::equals, s -> true);
		NET_INST.registerMessage(0, PacketSortInv.class,
				PacketSortInv::encode, PacketSortInv::new, PacketSortInv::handle);
	}
	
	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		//LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
	}
	
	private void enqueueIMC(final InterModEnqueueEvent event) {
		// some example code to dispatch IMC to another mod
		//InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
	}
	
	private void processIMC(final InterModProcessEvent event) {
		// some example code to receive and process InterModComms from other mods
		/*
        LOGGER.info("Got IMC {}", event.getIMCStream().
                map(m->m.getMessageSupplier().get()).
                collect(Collectors.toList()));*/
	}
	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
		server = event.getServer();
	}
	
	@SubscribeEvent
	public void onServerStopped(FMLServerStoppedEvent event) {
		server = null;
	}
	
	@OnlyIn(Dist.CLIENT)
	private static final Field guiLeftF = ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147003_i");
	@OnlyIn(Dist.CLIENT)
	private static final Field guiTopF = ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147009_r");
	
	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		//LOGGER.log(Level.INFO, event.getGui().getClass());
		if (event.getGui() instanceof ContainerScreen && !(event.getGui() instanceof CreativeScreen)) {
			Slot placement = getButtonPlacement(
					((ContainerScreen<?>)event.getGui()).getContainer().inventorySlots,
					slot -> slot.inventory instanceof PlayerInventory
					&& !PlayerInventory.isHotbar(slot.getSlotIndex()));
			if (placement != null) {
				try {
					event.addWidget(new InvTweaksButtonSort(
							guiLeftF.getInt(event.getGui())+placement.xPos+16,
							guiTopF.getInt(event.getGui())+placement.yPos,
							true));
				} catch (Exception e) {
					Throwables.throwIfUnchecked(e);
					throw new RuntimeException(e);
				}
			}
			if (!(event.getGui() instanceof DisplayEffectsScreen)) {
				placement = getButtonPlacement(
						((ContainerScreen<?>)event.getGui()).getContainer().inventorySlots,
						slot -> !(slot.inventory instanceof PlayerInventory));
				if (placement != null) {
					try {
						event.addWidget(new InvTweaksButtonSort(
								guiLeftF.getInt(event.getGui())+placement.xPos+16,
								guiTopF.getInt(event.getGui())+placement.yPos,
								false));
					} catch (Exception e) {
						Throwables.throwIfUnchecked(e);
						throw new RuntimeException(e);
					}
				}
			}
		}
		//event.addWidget(button);
	}
	
	public static final int MIN_SLOTS = 9;
	
	public static @Nullable Slot getButtonPlacement(Collection<Slot> slots, Predicate<Slot> filter) {
		if (slots.stream().filter(filter).count() < MIN_SLOTS) {
			return null;
		}
		// pick the rightmost slot first, then the topmost in case of a tie
		// TODO change button position algorithm?
		return slots.stream().filter(filter).max(
				Comparator.<Slot>comparingInt(s -> s.xPos)
				.thenComparingInt(s -> -s.yPos)).orElse(null);
	}
	
	Map<PlayerEntity, EnumMap<Hand, Item>> itemsCache = new WeakHashMap<>();
	Map<PlayerEntity, Object2IntMap<Item>> usedCache = new WeakHashMap<>();
	
	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.side == LogicalSide.SERVER) {
			EnumMap<Hand, Item> cached = itemsCache.computeIfAbsent(event.player, k -> new EnumMap<>(Hand.class));
			Object2IntMap<Item> ucached = usedCache.computeIfAbsent(event.player, k -> new Object2IntOpenHashMap<>());
			for (Hand hand: Hand.values()) {
				if (cached.get(hand) != null
						&& event.player.getHeldItem(hand).isEmpty()
						&& ((ServerPlayerEntity)event.player).getStats().getValue(
								Stats.ITEM_USED.get(cached.get(hand)))
						> ucached.getOrDefault(cached.get(hand), Integer.MAX_VALUE)) {
					//System.out.println("Item depleted");
					searchForSubstitute(event.player, hand, cached.get(hand));
				}
				ItemStack held = event.player.getHeldItem(hand);
				cached.put(hand, held.isEmpty() ? null : held.getItem());
				if (!held.isEmpty()) {
					ucached.put(held.getItem(),
							((ServerPlayerEntity)event.player).getStats().getValue(
									Stats.ITEM_USED.get(held.getItem())));
				}
			}
		}
	}
	
	private void searchForSubstitute(PlayerEntity ent, Hand hand, Item item) {
		// thank Simon for the flattening
		ent.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP).ifPresent(cap -> {
			for (int i=0; i<cap.getSlots(); ++i) {
				if (PlayerInventory.isHotbar(i)) {
					continue;
				}
				ItemStack cand = cap.extractItem(i, Integer.MAX_VALUE, true).copy();
				if (cand.getItem() == item) {
					cap.extractItem(i, Integer.MAX_VALUE, false);
					ent.setHeldItem(hand, cand);
				}
			}
		});
	}
	
	/*
    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            //LOGGER.info("HELLO from Register Block");
        }
    }*/
}