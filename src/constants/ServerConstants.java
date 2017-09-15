package constants;

import java.util.Arrays;
import java.util.List;

public class ServerConstants {

    public static final short MAPLE_VERSION = 145;
    public static final String MAPLE_PATCH = "1";
    public static final int MAPLE_LOCLE = 6;
    public static final String WZ_PATH = "./wz";
    public static final String MasterPass = "ericftw";
    public static final boolean BlockCS = false;
    public static final int Currency = 4000999;
    public static final boolean MerchantsUseCurrency = false; // Log Packets = true | Allow people to connect = false
    public static boolean DEBUG = true;
    public static boolean TESPIA = false; // true = uses GMS test server, for MSEA it does nothing though
    public static String SERVER_IP = "10.10.1.199";
    public static String SERVER_NAME = "啾咪谷 v145";
    private static final List<MapleLoginBalloon> mapleLoginBalloonList = Arrays.asList(
            new MapleLoginBalloon("歡迎來到" + ServerConstants.SERVER_NAME, 240, 140),
            new MapleLoginBalloon("禁止開外掛", 100, 150),
            new MapleLoginBalloon("遊戲愉快", 370, 150));
    public static String WzRevision = "1.0";
    public static String SourceRev = "1.0"; // initial revision
    public static boolean dropUndroppables = true;
    public static boolean moreThanOne = true;
    public static boolean Use_Localhost = false; // Only allow accounted admins to connect pass login server
    public static boolean USE_SECOND_PASSWORD_AUTH = false;
    public static boolean ADMIN_ONLY = false;
    public static int CHANNEL_LOAD = 150; // players per channel
    // 瞬移之石限制區域
    public static int[] VIP_ROCK_BLOCK = {180000000, 180000001};
    // 歡迎訊息
    public static String SERVER_MESSAGE = "歡迎來到啾咪谷v145.1，此端正在開發中 OuO/";
    public static String WELCOME_MESSAGE = "【歡迎訊息】";

    public static List<MapleLoginBalloon> getBalloons() {
        return mapleLoginBalloonList;
    }

    public static String getTip() {
        // Update these occasionally <3
        String[] tips = {
                "#rNEW items are available!#k", "#bNEW commands are available!#k", "#rVote for Munny!#k",
                "#bWe love our beauties! <3#k", "#rAuto Events#k give you Munny!", "#bMinigames are now available!#k",
                "#We support #rWindows 8#k!", ("Our #bWZ's REV#k is #r" + WzRevision),
                ("Our #bSource's REV#k is #r" + SourceRev), "#bDuh hello?#k", "Follow us on #bFacebook!#k",
                "#rY#k#bO#k#rL#k#bO#k", "#ruw0tm8?#k", "ALL HAIL #rTROLLS#k!!!", "#rEric#k is cool", "#bKevin#k is cool", "#rPaul#k is cool"
        };
        int tip = (int) Math.floor(Math.random() * tips.length);
        return tips[tip];
    }

    public static enum PlayerGMRank {

        NORMAL('@', 0),
        DONATOR('!', 1),
        SUPERDONATOR('!', 2),
        INTERN('!', 3),
        GM('!', 4),
        SUPERGM('!', 5),
        ADMIN('!', 6),
        GOD('!', 100);
        private char commandPrefix;
        private int level;

        PlayerGMRank(char ch, int level) {
            commandPrefix = ch;
            this.level = level;
        }

        public char getCommandPrefix() {
            return commandPrefix;
        }

        public int getLevel() {
            return level;
        }
    }

    public static enum CommandType {

        NORMAL(0),
        TRADE(1);
        private int level;

        CommandType(int level) {
            this.level = level;
        }

        public int getType() {
            return level;
        }
    }

    public static class MapleLoginBalloon {
        public int nX, nY;
        public String sMessage;

        public MapleLoginBalloon(String sMessage, int nX, int nY) {
            this.sMessage = sMessage;
            this.nX = nX;
            this.nY = nY;
        }
    }
}
