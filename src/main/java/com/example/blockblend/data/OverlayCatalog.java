package com.example.blockblend.data;
import net.minecraft.util.Identifier;
import java.util.List;
public final class OverlayCatalog {
 public static final List<Identifier> IDS=List.of(Identifier.of("blockblend","overlay/noise"),Identifier.of("blockblend","overlay/stripes"),Identifier.of("blockblend","overlay/scratches"));
 public static boolean contains(Identifier id){return id!=null&&IDS.contains(id);}
 private OverlayCatalog(){}
}
