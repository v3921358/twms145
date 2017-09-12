/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package handling.login;

import constants.ServerConstants;
import constants.WorldConfig;
import handling.MapleServerHandler;
import handling.channel.ChannelServer;
import handling.netty.ServerConnection;
import handling.world.World;
import tools.types.Pair;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class LoginServer {

    public static final int PORT = 8484;

    private static List<World> worlds = new ArrayList<>();
    private static LoginServer instance = null;
    private static ServerConnection acceptor;
    private static boolean finishedShutdown = true, adminOnly = false;
    private static HashMap<Integer, Pair<String, String>> loginAuth = new HashMap<>();
    private static HashSet<String> loginIPAuth = new HashSet<>();
    private static final ReentrantLock loginLock = new ReentrantLock();

    public static LoginServer getInstance() {
        if (instance == null) {
            instance = new LoginServer();
        }
        return instance;
    }

    public static void putLoginAuth(int chrid, String ip, String tempIP) {
        loginAuth.put(chrid, new Pair<>(ip, tempIP));
        loginIPAuth.add(ip);
    }

    public static Pair<String, String> getLoginAuth(int chrid) {
        return loginAuth.remove(chrid);
    }

    public static boolean containsIPAuth(String ip) {
        return loginIPAuth.contains(ip);
    }

    public static void removeIPAuth(String ip) {
        loginIPAuth.remove(ip);
    }

    public static void addIPAuth(String ip) {
        loginIPAuth.add(ip);
    }

    public void removeChannel(int worldId, final int channel) {
        World world = worlds.get(worldId);
        if (world != null) {
            world.removeChannel(channel);
        }
    }

    public ChannelServer getChannel(int world, int channel) {
        return getWorld(world).getChannel(channel);
    }

    public List<ChannelServer> getChannelsFromWorld(int world) {
        return getWorld(world).getChannels();
    }

    public List<ChannelServer> getAllChannels() {
        List<ChannelServer> channelz = new ArrayList<>();
        for (World world : worlds) {
            channelz.addAll(world.getChannels());
        }
        return channelz;
    }

    public String getChannelIP(int world, int channel) {
        return getWorld(world).getChannel(channel).getIP();
    }

    public static void run_startup_configurations() {

        adminOnly = ServerConstants.Admin_Only;
        acceptor = new ServerConnection(PORT, -1, MapleServerHandler.LOGIN_SERVER);

        WorldConfig[] worldConfigs = WorldConfig.values();
        for (WorldConfig worldConfig : worldConfigs) {
            if (worldConfig.isOn()) {
                System.out.printf("[World: %s ] Init...\n", worldConfig.name());
                World world = new World(worldConfig);
                worlds.add(world);
                world.initWorld();
            }
        }

        try {
            acceptor.run();
        } catch (InterruptedException e) {
            System.err.println("Binding to port " + PORT + " failed" + e);
            acceptor.close();
        }
    }

    public static World getWorld(int id) {
        for(World world: worlds) {
            if(world.getWorldId() == id)
                return  world;
        }
        return null;
    }

    public static List<World> getWorlds() {
        return worlds;
    }

    public static void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("Shutting down login...");
        acceptor.close();
        finishedShutdown = true; //nothing. lol
    }

    public static int getMaxCharacters(int world) {
        return getWorld(world).getMaxCharacter();
    }

    public static boolean isAdminOnly() {
        return adminOnly;
    }

    public static boolean isShutdown() {
        return finishedShutdown;
    }

    public static void setOn() {
        finishedShutdown = false;
    }

    public static ReentrantLock getLoginLock() {
        return loginLock;
    }
}
