package com.example.blockblend.client;
import com.example.blockblend.data.*;
public final class BlendClientState {
 public static int mode=0,overlay=0,strengthStep=5,opacityStep=5;
 public static BlendTag tag(){return new BlendTag(mode,strengthStep/10f,OverlayCatalog.IDS.get(overlay),opacityStep/10f);}
 public static String description(){return tag().getModeName()+" | "+tag().overlayId().getPath().substring(8)+" | pattern "+strengthStep*10+"% | opacity "+opacityStep*10+"%";}
 private BlendClientState(){}
}
