package com.ixnah.mc.ws;

import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/7/8 8:36
 */
@Mod(modid = MinecraftWebsocket.modId, name = MinecraftWebsocket.name, version = MinecraftWebsocket.version)
public class MinecraftWebsocket {
    static final String modId = "mcwebsocket";
    static final String name = "MCWebsocket";
    static final String version = "1.1";

    public MinecraftWebsocket() {
        try { // 由于Forge的反混淆排序在Coremod之后 所以说放到Mod加载的时候再添加Transformer
            Method registerTransformer = this.getClass().getClassLoader().getClass().getMethod("registerTransformer", String.class);
            registerTransformer.invoke(this.getClass().getClassLoader(), "com.ixnah.mc.ws.asm.MCWebsocketTransformer");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
