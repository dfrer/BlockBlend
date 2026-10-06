package com.example.blockblend;
import net.fabricmc.api.ModInitializer;
import org.slf4j.*;
import com.example.blockblend.registry.*;
public final class BlockBlendMod implements ModInitializer {
 public static final String MOD_ID="blockblend";
 public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
 public void onInitialize(){ModItems.init();ModNetworking.init();LOGGER.info("BlockBlend 0.3.1 initialized");}
}


