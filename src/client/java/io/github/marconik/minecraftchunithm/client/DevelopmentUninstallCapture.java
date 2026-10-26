package io.github.marconik.minecraftchunithm.client;

import io.github.marconik.minecraftchunithm.DevelopmentUninstallCheck;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

final class DevelopmentUninstallCapture {
    static void initialize(){
        if(!FabricLoader.getInstance().isDevelopmentEnvironment()||!Boolean.getBoolean("minecraftchunithm.uninstallQuickPlay"))return;
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            String result=DevelopmentUninstallCheck.result();if(result==null)return;
            try{java.nio.file.Files.writeString(client.gameDirectory.toPath().resolve("uninstall-checks.json"),result);}
            catch(java.io.IOException error){throw new RuntimeException(error);}
            client.stop();
        });
    }
}
