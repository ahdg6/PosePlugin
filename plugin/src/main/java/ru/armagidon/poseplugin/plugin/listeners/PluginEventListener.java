package ru.armagidon.poseplugin.plugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import ru.armagidon.poseplugin.PosePlugin;
import ru.armagidon.poseplugin.api.PosePluginAPI;
import ru.armagidon.poseplugin.api.events.StopPosingEvent;
import ru.armagidon.poseplugin.api.player.PosePluginPlayer;
import ru.armagidon.poseplugin.api.poses.EnumPose;
import ru.armagidon.poseplugin.api.poses.options.EnumPoseOption;
import ru.armagidon.poseplugin.api.utils.misc.VectorUtils;
import ru.armagidon.poseplugin.plugin.configuration.ConfigCategory;
import ru.armagidon.poseplugin.plugin.configuration.ConfigConstants;
import ru.armagidon.poseplugin.plugin.configuration.settings.SwimSettings;
import ru.armagidon.poseplugin.plugin.events.StopAnimationWithMessageEvent;

import static ru.armagidon.poseplugin.PosePlugin.PLAYERS_POSES;
import static ru.armagidon.poseplugin.api.poses.EnumPose.*;

public class PluginEventListener implements Listener
{

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        if(PosePlugin.checker != null){
            if(!PosePlugin.checker.uptodate && event.getPlayer().hasPermission("poseplugin.admin")){
                PosePlugin.checker.sendNotification(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPotionEffectObtain(EntityPotionEffectEvent event){
        if(!event.getEntity().getType().equals(EntityType.PLAYER)) return;
        Player player = (Player) event.getEntity();
        if(!PosePluginAPI.getAPI().getPlayerMap().containsPlayer(player)) return;
        PosePluginPlayer p = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(player.getName());
        if(!p.getPoseType().equals(EnumPose.LYING)) return;
        boolean preventInvisible = PosePlugin.getInstance().getConfig().getBoolean("lay.prevent-use-when-invisible");
        if( !preventInvisible ){
            if(event.getAction().equals(EntityPotionEffectEvent.Action.ADDED)){
                if(event.getNewEffect() !=null && event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY))
                    p.getPose().getProperty(EnumPoseOption.INVISIBLE).setValue(true);
            } else if(event.getAction().equals(EntityPotionEffectEvent.Action.REMOVED)||event.getAction().equals(EntityPotionEffectEvent.Action.CLEARED)){
                if(event.getOldEffect() != null && event.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY))
                    p.getPose().getProperty(EnumPoseOption.INVISIBLE).setValue(false);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event){
        //Call stop event
        if ( !PLAYERS_POSES.containsKey(event.getPlayer()) ) return;
        PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getPlayer().getName());

        if ( player.getPoseType() == SWIMMING ){
            if ( !event.getPlayer().isOnGround() ) return;
        } else if( player.getPoseType() == WAVING ){
            if( !ConfigConstants.isWaveShiftEnabled() ) return;
        } else if (player.getPoseType() == POINTING){
            if( !ConfigConstants.isPointShiftEnabled()) return;
        } else if ( player.getPoseType() == HANDSHAKING ){
            if( !ConfigConstants.isHandShakeShiftEnabled() ) return;
        }
        player.resetCurrentPose(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player)) return;

        if ( !PLAYERS_POSES.containsKey(event.getEntity()) ) return;

        PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getEntity().getName());
        if(player.getPoseType() == EnumPose.STANDING) return;
        boolean standUpWhenDamaged = PosePlugin.getInstance().getConfig().getBoolean(player.getPoseType().getName()+".stand-up-when-damaged");
        if (standUpWhenDamaged) {
            Bukkit.getPluginManager().callEvent(new StopAnimationWithMessageEvent(StopAnimationWithMessageEvent.StopCause.DAMAGE, player, player.getPoseType()));
            PLAYERS_POSES.remove(player.getHandle());
            player.resetCurrentPose(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {

        if( !PLAYERS_POSES.containsKey(event.getPlayer()) ) return;
        PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getPlayer().getName());
        if(player.getPoseType().equals(EnumPose.STANDING)) return;
        if(event.getNewGameMode().equals(GameMode.SPECTATOR))
            player.resetCurrentPose(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event){

        if ( !PLAYERS_POSES.containsKey(event.getPlayer()) ) return;

        if(event.getFrom().getWorld().equals(event.getTo().getWorld()))
            if(event.getFrom().clone().add(.5,0,.5).distance(event.getTo().clone().add(.5,0,.5)) < 1) return;
        if(!PosePluginAPI.getAPI().getPlayerMap().containsPlayer(event.getPlayer())) return;
        PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getPlayer());
        if(player.getPoseType().equals(EnumPose.STANDING)) return;
        if(event.getCause().equals(PlayerTeleportEvent.TeleportCause.PLUGIN)) return;

        player.resetCurrentPose(true);

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public final void onBlockBreak(BlockBreakEvent event) {

        VectorUtils.getNear(5,event.getPlayer()).forEach(near->{
            Block under = VectorUtils.getBlockOnLoc(near.getLocation()).getRelative(BlockFace.DOWN);
            PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getPlayer().getName());
            if(player.getPoseType().equals(EnumPose.STANDING)) return;
            if (event.getBlock().equals(under)) {
                player.resetCurrentPose(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public final void onMove(PlayerMoveEvent event){
        if ( !PLAYERS_POSES.containsKey(event.getPlayer())) return;
        PosePluginPlayer player = PosePluginAPI.getAPI().getPlayerMap().getPosePluginPlayer(event.getPlayer());
        if (player.getPoseType() != SWIMMING) return;

        boolean isSwimmingStatic = PosePlugin.getInstance().getConfigManager().get(ConfigCategory.SWIM, SwimSettings.STATIC);
        if (isSwimmingStatic){
            if (event.getTo().getX() != event.getFrom().getX() ||
                event.getTo().getY() != event.getFrom().getY() ||
                event.getTo().getZ() != event.getFrom().getZ())

                event.setTo(event.getFrom());
        }

    }

    @EventHandler
    public void onStop(StopPosingEvent event){

        if ( !PLAYERS_POSES.containsKey(event.getPlayer().getHandle()) ) return;
        if ( !event.isCancellable()) return;

        Bukkit.getPluginManager().callEvent(new StopAnimationWithMessageEvent(StopAnimationWithMessageEvent.StopCause.OTHER, event.getPlayer(), event.getPose()));
        PLAYERS_POSES.remove(event.getPlayer().getHandle());

    }

}
