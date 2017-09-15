package server;

import client.SkillFactory;
import client.inventory.MapleInventoryIdentifier;
import constants.ServerConstants;
import constants.WorldConstants;
import database.DatabaseConnection;
import handling.MapleServerHandler;
import handling.cashshop.CashShopServer;
import handling.channel.MapleGuildRanking;
import handling.login.LoginInformationProvider;
import handling.login.LoginServer;
import handling.world.World;
import handling.world.family.MapleFamily;
import handling.world.guild.MapleGuild;
import server.Timer.*;
import server.events.MapleOxQuizFactory;
import server.life.MapleLifeFactory;
import server.life.MapleMonsterInformationProvider;
import server.life.MobSkillFactory;
import server.life.PlayerNPC;
import server.maps.MapleMapFactory;
import server.quest.MapleQuest;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class Start {

    public static final Start instance = new Start();
    public static long startTime = System.currentTimeMillis();
    public static AtomicInteger CompletedLoadingThreads = new AtomicInteger(0);

    public static void main(final String args[]) throws InterruptedException {
        instance.run();
    }

    private void setAccountsLoginStatus() {
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE accounts SET loggedin = 0");
            ps.executeUpdate();
            ps.close();
            ps = DatabaseConnection.getConnection().prepareStatement("UPDATE guilds SET GP = 2147483647 WHERE guildid = 1");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            System.out.println(ex);
        }
    }

    private void initTimers() {
        WorldTimer.getInstance().start();
        PokeTimer.getInstance().start();
        EtcTimer.getInstance().start();
        MapTimer.getInstance().start();
        EventTimer.getInstance().start();
        BuffTimer.getInstance().start();
        PingTimer.getInstance().start();
    }

    public void run() throws InterruptedException {

        System.out.println("楓之谷v145模擬器 啟動中" + "." + ServerConstants.MAPLE_PATCH + "..");
        // Worlds
        WorldConstants.init();
        World.init();
        // Timers
        this.initTimers();
        // WorldConfig Handler
        MapleServerHandler.initiate();
        // Servers
        LoginServer.run_startup_configurations();
        CashShopServer.run_startup_configurations();
        // Information
        MapleItemInformationProvider.getInstance().runEtc();
        MapleMonsterInformationProvider.getInstance().load();
        MapleItemInformationProvider.getInstance().runItems();
        LoginServer.setOn();
        // Every other instance cache :)
        SkillFactory.load();
        LoginInformationProvider.getInstance();
        MapleGuildRanking.getInstance().load();
        MapleGuild.loadAll(); //(this);
        MapleFamily.loadAll(); //(this);
        MapleLifeFactory.loadQuestCounts();
        MapleQuest.initQuests();
        RandomRewards.load();
        MapleOxQuizFactory.getInstance();
        MapleCarnivalFactory.getInstance();
        //CharacterCardFactory.getInstance().initialize();
        MobSkillFactory.getInstance();
        SpeedRunner.loadSpeedRuns();
        MapleInventoryIdentifier.getInstance();
        MapleMapFactory.loadCustomLife();
        CashItemFactory.getInstance().initialize();
        PlayerNPC.loadAll();// touch - so we see database problems early...
        MapleMonsterInformationProvider.getInstance().addExtra();
        RankingWorker.run();
        System.out.println("Server is Opened");
    }
}
