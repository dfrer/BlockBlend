package com.example.blockblend.registry;
import com.example.blockblend.item.BlendApplicatorItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
public final class ModItems {
 public static final Item BLEND_APPLICATOR=Registry.register(Registries.ITEM,Identifier.of("blockblend","blend_applicator"),new BlendApplicatorItem(new Item.Settings().maxCount(1)));
 public static void init(){ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries->entries.add(BLEND_APPLICATOR));}
 private ModItems(){}
}


