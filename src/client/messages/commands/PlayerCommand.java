package client.messages.commands;
import client.MapleClient;
import client.MapleStat;
import constants.ServerConstants.PlayerGMRank;

import java.util.List;

public class PlayerCommand {

    public static PlayerGMRank getPlayerLevelRequired() {
        return PlayerGMRank.NORMAL;
    }

    public static class FixExp extends AbstractsCommandExecute {

        @Override
        public boolean execute(MapleClient c, List<String> args) {
            c.getPlayer().setExp(0);
            c.getPlayer().updateSingleStat(MapleStat.EXP, c.getPlayer().getExp());
            c.getPlayer().dropMessage(5, "經驗修復成功");
            return false;
        }

        @Override
        public String getHelpMessage() {
            return "@fixExp - 經驗值歸零";
        }
    }
}
