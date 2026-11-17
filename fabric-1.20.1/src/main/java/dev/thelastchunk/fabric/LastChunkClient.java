package dev.thelastchunk.fabric;

import dev.thelastchunk.RoundClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class LastChunkClient implements ClientModInitializer {
    private static int elapsed,round;
    private static boolean connected,water,food;
    private static String candidate="";
    @Override public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)-> { connected=false; elapsed=0; candidate=""; });
        ClientPlayNetworking.registerGlobalReceiver(TheLastChunk.SYNC,(client,handler,buf,sender)-> {
            int time=buf.readVarInt(),r=buf.readVarInt(); String id=buf.readString(); boolean w=buf.readBoolean(),f=buf.readBoolean();
            client.execute(()-> { connected=true; elapsed=time; water=w; food=f; candidate=id; round=r;
                if(elapsed<RoundClock.INTERVAL && client.currentScreen instanceof ChoiceScreen) client.setScreen(null);
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client-> {
            if(connected && client.player!=null && !client.player.isSpectator() && elapsed>=RoundClock.INTERVAL && client.currentScreen==null) client.setScreen(new ChoiceScreen());
        });
        HudRenderCallback.EVENT.register((draw,delta)-> {
            MinecraftClient c=MinecraftClient.getInstance(); if(!connected || c.player==null || c.options.hudHidden) return;
            int x=draw.getScaledWindowWidth()/2-91,y=draw.getScaledWindowHeight()-65;
            draw.fill(x-1,y-1,x+183,y+7,0xCC101521);
            draw.fill(x,y,x+182,y+6,0xFF263247);
            draw.fill(x,y,x+(int)(182.0*elapsed/RoundClock.INTERVAL),y+6,0xFF45D6A4);
            int seconds=(RoundClock.INTERVAL-elapsed+19)/20;
            String label=elapsed>=RoundClock.INTERVAL?"Выберите, что исчезнет":"Следующий выбор: "+String.format("%d:%02d",seconds/60,seconds%60);
            draw.drawCenteredTextWithShadow(c.textRenderer,label,x+91,y-11,0xFFFFFF);
        });
    }
    private static final class ChoiceScreen extends Screen {
        ChoiceScreen() { super(Text.literal("The Last Chunk")); }
        @Override protected void init() {
            int x=width/2-130,y=height/2-20;
            Text block=candidate.isEmpty()?Text.literal("Все блоки уже выбраны"):Text.literal("Блок: ").append(Registries.BLOCK.get(new Identifier(candidate)).getName());
            addChoice(x,y,block,0,!candidate.isEmpty());
            addChoice(x,y+32,Text.literal(water?"Вода уже удалена":"Удалить всю воду"),1,!water);
            addChoice(x,y+64,Text.literal(food?"Еда уже удалена":"Удалить всю еду"),2,!food);
        }
        private void addChoice(int x,int y,Text text,int option,boolean enabled) {
            ButtonWidget button=ButtonWidget.builder(text,b->{ var packet=PacketByteBufs.create(); packet.writeVarInt(round); packet.writeVarInt(option); ClientPlayNetworking.send(TheLastChunk.CHOOSE,packet); children().stream().filter(e->e instanceof ButtonWidget).forEach(e->((ButtonWidget)e).active=false); }).dimensions(x,y,260,24).build();
            button.active=enabled; addDrawableChild(button);
        }
        @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta) {
            renderBackground(draw); draw.fill(width/2-154,height/2-92,width/2+154,height/2+120,0xEE101A2A);
            draw.drawCenteredTextWithShadow(textRenderer,title,width/2,height/2-75,0xFF45D6A4);
            draw.drawCenteredTextWithShadow(textRenderer,"Раунд "+(round+1)+" · Один выбор для всего мира",width/2,height/2-54,0xFFFFFF);
            draw.drawCenteredTextWithShadow(textRenderer,"Удаление навсегда, во всех измерениях",width/2,height/2-40,0xFFABB9CC);
            super.render(draw,mouseX,mouseY,delta);
            draw.drawCenteredTextWithShadow(textRenderer,"Первый ответ игрока решает за всех",width/2,height/2+100,0xFFABB9CC);
        }
        @Override public boolean shouldPause() { return false; }
        @Override public boolean shouldCloseOnEsc() { return false; }
    }
}
