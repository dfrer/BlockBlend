package com.example.blockblend.item;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
/** Client input is registered in the client entrypoint; common item never loads client classes. */
public final class BlendApplicatorItem extends Item {
 public BlendApplicatorItem(Settings s){super(s);}
 @Override public ActionResult useOnBlock(ItemUsageContext c){return ActionResult.SUCCESS;}
}


