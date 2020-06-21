package com.ixnah.mc.ws;

import lombok.SneakyThrows;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * @author 寒兮
 * @version 1.0
 * @date 2020/3/29 17:36
 */
public class WSClassTransformer implements IClassTransformer {

    @Override
    @SneakyThrows
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("net.minecraft.network.NetworkManager")) {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            for (MethodNode methodNode : node.methods) {
                if (methodNode.name.equals("provideLanClient") || methodNode.name.equals("func_150726_a")) {
                    String patchClassName = "com.ixnah.mc.ws.WebSocket".replace(".", "/");
                    String patchMethodName = "provideLanClient";
                    InsnList insn = methodNode.instructions;
                    insn.clear();
                    insn.add(new LabelNode());
                    insn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insn.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    insn.add(new MethodInsnNode(Opcodes.INVOKESTATIC, patchClassName, patchMethodName, methodNode.desc, false));
                    insn.add(new InsnNode(Opcodes.ARETURN));
                    insn.add(new LabelNode());
                    break;
                }
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            basicClass = writer.toByteArray();
        }
        return basicClass;
    }
}
