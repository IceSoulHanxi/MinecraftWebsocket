package com.ixnah.mc.ws.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/7/8 8:40
 */
@SuppressWarnings("unused")
public class MCWebsocketTransformer implements IClassTransformer {

    private static final String METHOD1_ = "func_181124_a";
    private static final String METHOD1 = "createNetworkManagerAndConnect";
    private static final String METHOD2 = "connect";
    private static final String METHOD2_ = "func_146367_a";

    private static final String CLASS = "com/ixnah/mc/ws/util/ConnectUtil";
    private static final String CREATE_CONNECTION = "createConnection";
    private static final String CREATE_CONNECTION_DESC = "(Lnet/minecraft/client/multiplayer/ServerData;Z)Lnet/minecraft/network/NetworkManager;";
    private static final String INIT_DESC = "(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/ServerData;)V";
    private static final String CONNECT = "connect";
    private static final String CONNECT_DESC = "(Lnet/minecraft/client/multiplayer/GuiConnecting;Lnet/minecraft/client/multiplayer/ServerData;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.client.network.ServerPinger"))
            return transformServerPinger(basicClass);
        if (transformedName.equals("net.minecraft.client.multiplayer.GuiConnecting"))
            return transformGuiConnecting(basicClass);
        return basicClass;
    }

//      ServerPinger.ping()
//      ALOAD 2                 修改 ALOAD 1
//      INVOKEVIRTUAL           删除
//      INVOKESTATIC            删除
//      ALOAD 2                 删除
//      INVOKEVIRTUAL           删除
//      ICONST_0                保留
//      INVOKESTATIC            修改 com.ixnah.mc.ws.util.ConnectUtil.createConnection()

    private byte[] transformServerPinger(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        f1: for (MethodNode methodNode : node.methods) {
            if (!methodNode.name.equals("func_147224_a") && !methodNode.name.equals("ping")) continue;
            ListIterator<AbstractInsnNode> insnIterator = methodNode.instructions.iterator();
            while (insnIterator.hasNext()) {
                AbstractInsnNode insnNode = insnIterator.next();
                if (!(insnNode instanceof MethodInsnNode)) continue;
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (!methodInsnNode.name.equals(METHOD1_) && !methodInsnNode.name.equals(METHOD1)) continue;
                methodInsnNode.owner = CLASS;
                methodInsnNode.name = CREATE_CONNECTION;
                methodInsnNode.desc = CREATE_CONNECTION_DESC;
                insnIterator.previous();
                insnIterator.previous();
                for (int i = 0; i < 5; i++) {
                    insnIterator.previous();
                    insnIterator.remove();
                }
                insnIterator.add(new VarInsnNode(ALOAD, 1));
                break f1;
            }
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private byte[] transformGuiConnecting(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, 0);
        f1: for (MethodNode methodNode : node.methods) {
            if (!methodNode.name.equals("<init>") || !methodNode.desc.equals(INIT_DESC)) continue;
            ListIterator<AbstractInsnNode> insnIterator = methodNode.instructions.iterator();
            while (insnIterator.hasNext()) {
                AbstractInsnNode insnNode = insnIterator.next();
                if (!(insnNode instanceof MethodInsnNode)) continue;
                MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                if (!methodInsnNode.name.equals(METHOD2_) && !methodInsnNode.name.equals(METHOD2)) continue;
                methodInsnNode.setOpcode(INVOKESTATIC);
                methodInsnNode.owner = CLASS;
                methodInsnNode.name = CONNECT;
                methodInsnNode.desc = CONNECT_DESC;
                insnIterator.previous();
                for (int i = 0; i < 5; i++) {
                    insnIterator.previous();
                    insnIterator.remove();
                }
                insnIterator.add(new VarInsnNode(ALOAD, 0));
                insnIterator.add(new VarInsnNode(ALOAD, 3));
                break f1;
            }
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
